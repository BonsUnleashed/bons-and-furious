package bons.pure.terrain;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.slf4j.Logger;

/**
 * Bons and Furious switch terrain_final_density_reuse (vanilla 1.20.1 world generation). SRG member names.
 *
 * NoiseChunk's constructor maps the noise router with its wrap visitor (pass 1), then maps
 * cacheAllInCell(add(finalDensity, beardifier)) with a fresh wrap visitor (pass 2). Pass 2 walks the already mapped
 * final density again: every record and spline is rebuilt, hashed down its whole subtree and looked up in the wrap
 * table, and the lookup hands back the object pass 1 produced.
 *
 * The coremods wrap the pass-2 visitor in {@link Pass2} and let each mapAll that goes through the table ask
 * {@link #reuse} first. A node is returned unchanged exactly when vanilla would return that same object: it is the value
 * of a table entry that maps it to itself (or, for a NoiseChunk cache, of the marker entry that created it) and every
 * child maps to an equal result, so vanilla's rebuilt copy equals that key and the lookup returns the node. Mapped,
 * Clamp and MulOrAdd never go through the table (vanilla returns a fresh equal copy); they keep running their own code,
 * and their copy is confirmed equal by running that code with a no-op visitor. Everything else (other mods' density
 * functions, nodes new in pass 2, per-chunk objects such as Bumblezone's biome noise) runs vanilla code. For the
 * skipped nodes vanilla inserts nothing into the table and creates no caches, so the resulting graph, caches and table
 * are the same object for object; only hashing, equality checks and rebuilds of unchanged nodes are skipped.
 *
 * The density function records are protected nested classes, so they are resolved by name and read through method
 * handles; if that fails the optimization stays off and generation runs the original code.
 */
public final class FinalDensityReuse {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DFS = "net.minecraft.world.level.levelgen.DensityFunctions$";

    /** Runtime switch (the config switch acts when classes are transformed). -Dbons.pure.finalDensityReuse=false also turns it off. */
    static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.finalDensityReuse", "true"));
    static final boolean METRICS = Boolean.getBoolean("bons_and_furious.finalDensityReuse.metrics");
    /** Pass-2 traversals that used the reuse visitor, and nodes returned without rebuilding (only with -Dbons.pure.finalDensityReuse.metrics=true). */
    public static final LongAdder PASSES = new LongAdder(), REUSED = new LongAdder();
    private static volatile boolean warned;

    private static final int OTHER = 0, CANON = 1, CACHE = 2, LEAF = 3, COPY = 4;
    private static final Class<?> AP2, RANGE_CHOICE, BLEND_DENSITY, WEIRD, SHIFTED_NOISE, SPLINE, COORDINATE, MARKER,
            NOISE, SHIFT, SHIFT_A, SHIFT_B, MAPPED, CLAMP, MUL_OR_ADD;
    private static final MethodHandle AP2_A, AP2_B, RANGE_INPUT, RANGE_IN, RANGE_OUT, BLEND_INPUT, WEIRD_INPUT, SHIFT_X, SHIFT_Y, SHIFT_Z,
            MAPPED_INPUT, CLAMP_INPUT, MUL_OR_ADD_INPUT, SPLINE_CUBIC, COORDINATE_HOLDER, SPLINE_CREATE;
    static final boolean READY;

    static {
        Class<?>[] c = new Class<?>[15];
        MethodHandle[] h = new MethodHandle[16];
        boolean ready = false;
        try {
            String[] names = {"Ap2", "RangeChoice", "BlendDensity", "WeirdScaledSampler", "ShiftedNoise", "Spline", "Spline$Coordinate", "Marker",
                    "Noise", "Shift", "ShiftA", "ShiftB", "Mapped", "Clamp", "MulOrAdd"};
            ClassLoader loader = DensityFunction.class.getClassLoader();
            for (int i = 0; i < names.length; i++) c[i] = Class.forName(DFS + names[i], false, loader);
            h[0] = getter(c[0], "m_207185_", DensityFunction.class);     // Ap2.argument1
            h[1] = getter(c[0], "m_207190_", DensityFunction.class);     // Ap2.argument2
            h[2] = getter(c[1], "f_208823_", DensityFunction.class);     // RangeChoice.input
            h[3] = getter(c[1], "f_208826_", DensityFunction.class);     // RangeChoice.whenInRange
            h[4] = getter(c[1], "f_208827_", DensityFunction.class);     // RangeChoice.whenOutOfRange
            h[5] = getter(c[2], "m_207189_", DensityFunction.class);     // BlendDensity.input
            h[6] = getter(c[3], "m_207189_", DensityFunction.class);     // WeirdScaledSampler.input
            h[7] = getter(c[4], "f_208924_", DensityFunction.class);     // ShiftedNoise.shiftX
            h[8] = getter(c[4], "f_208925_", DensityFunction.class);     // ShiftedNoise.shiftY
            h[9] = getter(c[4], "f_208926_", DensityFunction.class);     // ShiftedNoise.shiftZ
            h[10] = getter(c[12], "m_207305_", DensityFunction.class);   // Mapped.input
            h[11] = getter(c[13], "m_207305_", DensityFunction.class);   // Clamp.input
            h[12] = getter(c[14], "m_207305_", DensityFunction.class);   // MulOrAdd.input
            h[13] = getter(c[5], "f_211702_", CubicSpline.class);        // Spline.spline
            h[14] = getter(c[6], "f_224122_", Holder.class);             // Spline.Coordinate.function
            Method create = CubicSpline.Multipoint.class.getDeclaredMethod("m_216143_", ToFloatFunction.class, float[].class, List.class, float[].class);
            create.setAccessible(true);
            h[15] = MethodHandles.lookup().unreflect(create).asType(MethodType.methodType(Object.class, Object.class, float[].class, List.class, float[].class));
            ready = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: terrain_final_density_reuse is inactive because the density function classes are not the supported 1.20.1 layout ({})", t.toString());
        }
        AP2 = c[0]; RANGE_CHOICE = c[1]; BLEND_DENSITY = c[2]; WEIRD = c[3]; SHIFTED_NOISE = c[4]; SPLINE = c[5]; COORDINATE = c[6]; MARKER = c[7];
        NOISE = c[8]; SHIFT = c[9]; SHIFT_A = c[10]; SHIFT_B = c[11]; MAPPED = c[12]; CLAMP = c[13]; MUL_OR_ADD = c[14];
        AP2_A = h[0]; AP2_B = h[1]; RANGE_INPUT = h[2]; RANGE_IN = h[3]; RANGE_OUT = h[4]; BLEND_INPUT = h[5]; WEIRD_INPUT = h[6];
        SHIFT_X = h[7]; SHIFT_Y = h[8]; SHIFT_Z = h[9]; MAPPED_INPUT = h[10]; CLAMP_INPUT = h[11]; MUL_OR_ADD_INPUT = h[12];
        SPLINE_CUBIC = h[13]; COORDINATE_HOLDER = h[14]; SPLINE_CREATE = h[15];
        READY = ready;
    }

    private static final ClassValue<Integer> KIND = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> c) {
            return kindOf(c);
        }
    };

    private static final ClassValue<Boolean> PLAIN_NOISE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return c.getMethod("m_213918_", DensityFunction.NoiseHolder.class).getDeclaringClass() == DensityFunction.Visitor.class;
            } catch (NoSuchMethodException e) {
                return Boolean.FALSE;
            }
        }
    };

    private FinalDensityReuse() {
    }

    private static MethodHandle getter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        Method m = owner.getDeclaredMethod(name);
        if (m.getParameterCount() != 0 || !type.isAssignableFrom(m.getReturnType())) throw new NoSuchMethodException(owner.getName() + '.' + name + " is not the expected accessor");
        m.setAccessible(true);
        return MethodHandles.lookup().unreflect(m).asType(MethodType.methodType(type == DensityFunction.class ? DensityFunction.class : Object.class, Object.class));
    }

    private static int kindOf(Class<?> c) {
        if (c == AP2 || c == RANGE_CHOICE || c == BLEND_DENSITY || c == WEIRD || c == SHIFTED_NOISE || c == SPLINE) return CANON;
        if (c == NOISE || c == SHIFT || c == SHIFT_A || c == SHIFT_B) return LEAF;
        if (c == MAPPED || c == CLAMP || c == MUL_OR_ADD) return COPY;
        try {
            Class<?> declaring = c.getMethod("m_207456_", DensityFunction.Visitor.class).getDeclaringClass();
            if (declaring == DensityFunctions.MarkerOrMarked.class && c != MARKER) return CACHE;
            if (declaring == DensityFunction.SimpleFunction.class) return LEAF;
        } catch (NoSuchMethodException e) {
            return OTHER;
        }
        return OTHER;
    }

    /** Called by NoiseChunk.&lt;init&gt; (coremod) with the pass-2 wrap visitor and the wrap table. */
    public static DensityFunction.Visitor pass2(DensityFunction.Visitor visitor, Map<DensityFunction, DensityFunction> table) {
        if (!READY || !enabled || visitor == null || visitor instanceof Pass2) return visitor;
        if (!(table instanceof Object2ObjectOpenHashMap || table instanceof HashMap)) return visitor;   // structural tables only
        try {
            if (!PLAIN_NOISE.get(visitor.getClass())) return visitor;
            Pass2 pass = new Pass2(visitor, table);
            if (METRICS) PASSES.increment();
            return pass;
        } catch (Throwable t) {
            disable(t);
            return visitor;
        }
    }

    /** Called first by each patched mapAll: true = vanilla would return {@code self}, so return it without rebuilding. */
    public static boolean reuse(DensityFunction self, DensityFunction.Visitor visitor) {
        Pass2 pass;
        if (visitor instanceof Pass2 p) pass = p;
        else if (visitor instanceof Sim s) pass = s.owner;
        else return false;
        if (pass.broken) return false;
        try {
            boolean same = pass.same(self);
            if (same && METRICS && visitor == pass) REUSED.increment();
            return same;
        } catch (Throwable t) {
            pass.broken = true;
            disable(t);
            return false;
        }
    }

    private static void disable(Throwable t) {
        enabled = false;
        if (!warned) {
            warned = true;
            LOGGER.warn("Bons and Furious: terrain_final_density_reuse turned itself off after an unexpected error; world generation continues with the original code", t);
        }
    }

    /** The pass-2 visitor: vanilla's wrap visitor plus the table snapshot and the per-traversal analysis. */
    static final class Pass2 implements DensityFunction.Visitor {
        private static final int SELF = 1, MARKER_CACHE = 2, BOTH = SELF | MARKER_CACHE;
        final DensityFunction.Visitor delegate;
        final Sim sim = new Sim(this);
        /** Table values with their role: SELF = the entry maps the value to itself, MARKER_CACHE = a NoiseChunk cache created from its marker entry. */
        private final IdentityHashMap<DensityFunction, Integer> members;
        private final IdentityHashMap<DensityFunction, Boolean> same = new IdentityHashMap<>();
        private final IdentityHashMap<DensityFunction, Boolean> equal = new IdentityHashMap<>();
        private final IdentityHashMap<Object, Boolean> splines = new IdentityHashMap<>();
        boolean broken;

        Pass2(DensityFunction.Visitor delegate, Map<DensityFunction, DensityFunction> table) {
            this.delegate = delegate;
            this.members = new IdentityHashMap<>(Math.max(32, table.size()));
            if (table instanceof Object2ObjectOpenHashMap<DensityFunction, DensityFunction> fast) {
                for (Object2ObjectMap.Entry<DensityFunction, DensityFunction> e : Object2ObjectMaps.fastIterable(fast)) add(e.getKey(), e.getValue());
            } else {
                for (Map.Entry<DensityFunction, DensityFunction> e : table.entrySet()) add(e.getKey(), e.getValue());
            }
        }

        private void add(DensityFunction key, DensityFunction value) {
            int role;
            if (key == null || value == null) return;
            if (key == value) role = SELF;
            else if (key.getClass() == MARKER && value instanceof DensityFunctions.MarkerOrMarked cache && value.getClass() != MARKER
                    && ((DensityFunctions.MarkerOrMarked) key).m_207136_() == cache.m_207136_() && ((DensityFunctions.MarkerOrMarked) key).m_207056_() == cache.m_207056_())
                role = MARKER_CACHE;
            else return;
            Integer prior = members.get(value);
            members.put(value, prior == null ? role : prior | role);
        }

        @Override
        public DensityFunction m_214017_(DensityFunction function) {
            return delegate.m_214017_(function);
        }

        @Override
        public DensityFunction.NoiseHolder m_213918_(DensityFunction.NoiseHolder noise) {
            return delegate.m_213918_(noise);
        }

        /** Would vanilla n.mapAll(this) return n itself? */
        boolean same(DensityFunction n) throws Throwable {
            Boolean known = same.get(n);
            if (known != null) return known;
            boolean result = computeSame(n);
            same.put(n, result);
            return result;
        }

        private boolean computeSame(DensityFunction n) throws Throwable {
            Integer known = members.get(n);
            if (known == null) return false;
            int role = known;
            if (role == BOTH) return false;
            return switch (KIND.get(n.getClass())) {
                case CACHE -> role == MARKER_CACHE && equal(((DensityFunctions.MarkerOrMarked) n).m_207056_());
                case LEAF -> role == SELF;
                case CANON -> role == SELF && childrenEqual(n);
                default -> false;
            };
        }

        /** Would vanilla x.mapAll(this) return an object equal to x (x itself for everything except the fresh-copy kinds)? */
        boolean equal(DensityFunction x) throws Throwable {
            if (KIND.get(x.getClass()) != COPY) return same(x);
            Boolean known = equal.get(x);
            if (known != null) return known;
            Class<?> c = x.getClass();
            MethodHandle getInput = c == MAPPED ? MAPPED_INPUT : c == CLAMP ? CLAMP_INPUT : MUL_OR_ADD_INPUT;
            DensityFunction input = (DensityFunction) getInput.invokeExact((Object) x);
            // Only known, verified children are reached under the no-op visitor, so no unknown code runs here.
            boolean result = equal(input) && x.equals(x.m_207456_(sim));
            equal.put(x, result);
            return result;
        }

        private boolean childrenEqual(DensityFunction n) throws Throwable {
            Class<?> c = n.getClass();
            if (c == AP2) return equal((DensityFunction) AP2_A.invokeExact((Object) n)) && equal((DensityFunction) AP2_B.invokeExact((Object) n));
            if (c == RANGE_CHOICE) return equal((DensityFunction) RANGE_INPUT.invokeExact((Object) n)) && equal((DensityFunction) RANGE_IN.invokeExact((Object) n))
                    && equal((DensityFunction) RANGE_OUT.invokeExact((Object) n));
            if (c == BLEND_DENSITY) return equal((DensityFunction) BLEND_INPUT.invokeExact((Object) n));
            if (c == WEIRD) return equal((DensityFunction) WEIRD_INPUT.invokeExact((Object) n));
            if (c == SHIFTED_NOISE) return equal((DensityFunction) SHIFT_X.invokeExact((Object) n)) && equal((DensityFunction) SHIFT_Y.invokeExact((Object) n))
                    && equal((DensityFunction) SHIFT_Z.invokeExact((Object) n));
            if (c == SPLINE) return spline((Object) SPLINE_CUBIC.invokeExact((Object) n));
            return false;
        }

        /** Would vanilla's rebuild of this spline (new coordinates, create() for every point) equal it? */
        private boolean spline(Object cubic) throws Throwable {
            if (cubic instanceof CubicSpline.Constant<?, ?>) return true;
            if (!(cubic instanceof CubicSpline.Multipoint<?, ?> point)) return false;
            Boolean known = splines.get(point);
            if (known != null) return known;
            boolean result = false;
            Object coordinate = point.f_184319_();
            if (coordinate != null && coordinate.getClass() == COORDINATE
                    && (Object) COORDINATE_HOLDER.invokeExact(coordinate) instanceof Holder.Direct<?> direct && direct.m_203334_() instanceof DensityFunction function
                    && equal(function)) {
                result = true;
                List<?> values = point.f_184321_();
                for (Object value : values) {
                    if (!spline(value)) {
                        result = false;
                        break;
                    }
                }
                // The stored bounds must be the ones create() computes, as they are for every spline vanilla rebuilt.
                if (result) result = point.equals((Object) SPLINE_CREATE.invokeExact(coordinate, point.f_184320_(), (List) point.f_184321_(), point.f_184322_()));
            }
            splines.put(point, result);
            return result;
        }
    }

    /** No-op visitor used only to run the vanilla rebuild of Mapped/Clamp/MulOrAdd over already verified children. */
    static final class Sim implements DensityFunction.Visitor {
        final Pass2 owner;

        Sim(Pass2 owner) {
            this.owner = owner;
        }

        @Override
        public DensityFunction m_214017_(DensityFunction function) {
            return function;
        }
    }
}
