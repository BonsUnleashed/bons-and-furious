package bons.furious.patch.vanilla_zero_term;

import bons.pure.terrain.DensityAudit;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_density_zero_term_skip (Minecraft 1.20.1 world generation; both sides, it acts wherever terrain
 * is generated). SRG member names.
 *
 * <p>A datapack term switched off by a constant 0 (Tectonic 3.0.17's {@code lava_tunnel/total = mul(config lava_tunnels,
 * min(0, spline(...)))} with lava tunnels off) is folded by Minecraft into "multiply by 0.0" (DensityFunctions$MulOrAdd) whose
 * input is still evaluated for every block of every cell, then multiplied by zero: the vanilla unfolded multiplication would
 * have skipped it, the fold loses that. Such a term contributes only +0.0 or -0.0 to the addition it sits in. For every finite
 * value m except -0.0, m + (+0.0) and m + (-0.0) are both m, bit for bit (m = +0.0 included), so the addition's result does
 * not depend on the term. With the switch an addition (DensityFunctions$Ap2 ADD) one of whose arguments is such a term fills
 * the other argument first and evaluates the term only for the elements whose value is -0.0 or NaN, where it computes exactly
 * what the original adds (in the original operand order). Every density value is identical, bit for bit.
 *
 * <p>Which additions qualify (decided once per Ap2 node, at its first fill): the term is a MulOrAdd of type MUL with argument 0.0
 * whose input has finite bounds and holds only density function types DensityAudit knows as pure, and none of the NoiseChunk
 * caches whose answer depends on the order of evaluation (CacheOnce, CacheAllInCell, NoiseInterpolator) nor a blender or
 * beardifier: then the input is finite everywhere (so the term is a zero, never NaN) and evaluating it per element computes
 * what the array fill would. The fill takes the short path only when the context provider only positions the NoiseChunk:
 * the NoiseChunk itself in a cell fill, or vanilla_noise_column_cell_fill's column provider in a height query's cell fill
 * ({@link #positioning}; the slice provider's forIndex advances a counter the caches read, so it keeps the original).
 * Everything else runs the original code.
 *
 * <p>-Dbons_and_furious.zeroTerm=false keeps the original (the config switch acts at class transformation); .metrics=true counts
 * fills taken short and elements recomputed; .verify=true (rigs) never takes the short path and, after every original fill of
 * a qualifying addition, computes the short path's answer into a scratch array and compares it bit for bit (a difference is
 * logged and counted).
 */
public final class ZeroTerms {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.zeroTerm.verify");
    public static final boolean ACTIVE = !VERIFY && !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.zeroTerm", "true"));
    public static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.zeroTerm.metrics");
    public static final byte UNDECIDED = 0, NONE = 1, SECOND = 2, FIRST = 3;
    private static final long NEG_ZERO = Double.doubleToRawLongBits(-0.0);
    static final String DFS = "net.minecraft.world.level.levelgen.DensityFunctions$", NC = "net.minecraft.world.level.levelgen.NoiseChunk$";
    /** Types that make an input unsuitable: caches whose answer depends on evaluation order, blending and structure terrain. */
    static final Set<String> STATEFUL = Set.of(NC + "CacheOnce", NC + "CacheAllInCell", NC + "NoiseInterpolator", DFS + "BlendDensity",
            DFS + "BlendAlpha", DFS + "BlendOffset", NC + "BlendAlpha", NC + "BlendOffset", DFS + "BeardifierMarker",
            "net.minecraft.world.level.levelgen.Beardifier", DFS + "Marker");
    public static final LongAdder SHORT_FILLS = new LongAdder(), RECOMPUTED = new LongAdder(), VERIFIED = new LongAdder(), WRONG = new LongAdder();
    private static volatile boolean announced;
    private static final String COLUMN = "bons.furious.patch.vanilla_cellfill.ColumnCellFill$Column";
    private static volatile Class<?> columnClass;
    private static final Field AP2_TYPE, MOA_TYPE, MOA_INPUT, MOA_ARGUMENT;

    static {
        Field a = null, b = null, c = null, d = null;
        try {
            a = field(Class.forName(DFS + "Ap2"), "f_208397_");
            Class<?> moa = Class.forName(DFS + "MulOrAdd");
            b = field(moa, "f_208746_");
            c = field(moa, "f_208747_");
            d = field(moa, "f_208750_");
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_density_zero_term_skip cannot read the density records ({}); the original code runs", t.toString());
        }
        AP2_TYPE = a;
        MOA_TYPE = b;
        MOA_INPUT = c;
        MOA_ARGUMENT = d;
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious vanilla_density_zero_term_skip: fills "
                    + (VERIFY ? "that would be short " : "taken short ") + SHORT_FILLS.sum() + ", elements recomputed " + RECOMPUTED.sum()
                    + (VERIFY ? ", fills compared " + VERIFIED.sum() + ", fills that differ " + WRONG.sum() : "")),
                    "Bons and Furious zero-term summary"));
        }
    }

    private ZeroTerms() {
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (Field f : DensityAudit.fields(c)) if (f.getName().equals(name)) return f;
        throw new NoSuchFieldException(c.getName() + "." + name);
    }

    /** The plan of an Ap2 node: NONE, SECOND (argument2 is the zero term) or FIRST (argument1 is). Never throws. */
    public static byte plan(Object ap2, DensityFunction argument1, DensityFunction argument2) {
        try {
            if (AP2_TYPE == null || !"ADD".equals(((Enum<?>) AP2_TYPE.get(ap2)).name())) return NONE;
            byte p = zeroTerm(argument2) ? SECOND : zeroTerm(argument1) ? FIRST : NONE;
            if (p != NONE && !announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_density_zero_term_skip applies (an addition of a term multiplied by a constant 0 is filled without "
                        + "evaluating that term){}", VERIFY ? " - verify mode, every fill computed both ways" : "");
            }
            return p;
        } catch (Throwable t) {
            return NONE;
        }
    }

    /** A MulOrAdd of type MUL with argument 0.0 over a finite, pure, order-independent input. */
    static boolean zeroTerm(DensityFunction f) throws ReflectiveOperationException {
        if (f == null || !f.getClass().getName().equals(DFS + "MulOrAdd")) return false;
        if (!"MUL".equals(((Enum<?>) MOA_TYPE.get(f)).name()) || MOA_ARGUMENT.getDouble(f) != 0.0) return false;
        DensityFunction input = (DensityFunction) MOA_INPUT.get(f);
        if (input == null || !Double.isFinite(input.m_207402_()) || !Double.isFinite(input.m_207401_())) return false;
        return DensityAudit.refusal(input) == null && orderIndependent(input, new IdentityHashMap<>());
    }

    private static boolean orderIndependent(Object o, Map<Object, Boolean> seen) throws ReflectiveOperationException {
        if (o == null || seen.put(o, Boolean.TRUE) != null) return true;
        if (STATEFUL.contains(o.getClass().getName())) return false;
        for (Object c : children(o)) if (!orderIndependent(c, seen)) return false;
        return true;
    }

    /** The density functions, splines and spline coordinates directly below a node (as DensityAudit's walk sees them). */
    private static List<Object> children(Object o) throws ReflectiveOperationException {
        List<Object> out = new ArrayList<>(4);
        for (Field f : DensityAudit.fields(o.getClass())) {
            if (f.getType().isPrimitive()) continue;
            add(out, f.get(o));
        }
        return out;
    }

    private static void add(List<Object> out, Object v) {
        if (v == null) return;
        if (v instanceof DensityFunction || v instanceof CubicSpline<?, ?>) out.add(v);
        else if (v instanceof Holder<?> h) {
            Object x = h.m_203334_();
            if (x instanceof DensityFunction) out.add(x);
        } else if (v instanceof List<?> l) {
            for (Object e : l) add(out, e);
        } else if (v.getClass().getName().equals(DFS + "Spline$Coordinate")) out.add(v);
    }

    /**
     * True for the context providers whose forIndex only positions the NoiseChunk (in-cell x / y / z and the array index) and
     * hands it back, without moving a counter: the NoiseChunk itself (its cell fills) and the column provider of
     * vanilla_noise_column_cell_fill (ColumnCellFill.Column: the same positions for the queried column of a height query; known
     * by name, so this group needs nothing of that one). The slice provider of the corner fills advances interpolationCounter
     * in forIndex and is not one of them.
     */
    public static boolean positioning(DensityFunction.ContextProvider cp) {
        if (cp instanceof NoiseChunk) return true;
        if (cp == null) return false;
        Class<?> c = cp.getClass(), k = columnClass;
        if (k != null) return c == k;
        if (c.getName().equals(COLUMN)) {
            columnClass = c;
            return true;
        }
        return false;
    }

    private static boolean special(double d) {
        return Double.doubleToRawLongBits(d) == NEG_ZERO || d != d;
    }

    /**
     * The short fill of an addition {@code other + zero} (zeroFirst false: argument1 = other) or {@code zero + other} (zeroFirst
     * true): the other argument is filled into ds, the zero term computed only where the result can depend on it.
     */
    public static void fill(double[] ds, DensityFunction.ContextProvider cp, DensityFunction other, DensityFunction zero, boolean zeroFirst) {
        other.m_207362_(ds, cp);
        int recomputed = 0;
        for (int i = 0; i < ds.length; i++) {
            double d = ds[i];
            if (special(d)) {
                double z = zero.m_207386_(cp.m_207263_(i));
                ds[i] = zeroFirst ? z + d : d + z;
                recomputed++;
            }
        }
        if (METRICS) {
            SHORT_FILLS.increment();
            if (recomputed != 0) RECOMPUTED.add(recomputed);
        }
    }

    /** Verify mode, after the original fill: the short path's answer, computed into a scratch array, compared bit for bit. */
    public static void verify(double[] ds, DensityFunction.ContextProvider cp, DensityFunction other, DensityFunction zero, boolean zeroFirst) {
        double[] mine = new double[ds.length];
        fill(mine, cp, other, zero, zeroFirst);
        VERIFIED.increment();
        for (int i = 0; i < ds.length; i++) {
            if (Double.doubleToRawLongBits(mine[i]) != Double.doubleToRawLongBits(ds[i])) {
                WRONG.increment();
                LOGGER.error("Bons and Furious: vanilla_density_zero_term_skip verify: element {} is {} in the original fill and {} on the short path",
                        i, ds[i], mine[i]);
                return;
            }
        }
    }
}
