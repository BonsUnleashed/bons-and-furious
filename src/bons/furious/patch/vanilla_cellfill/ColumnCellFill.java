package bons.furious.patch.vanilla_cellfill;

import bons.pure.terrain.DensityAudit;
import com.google.common.collect.MapMaker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_noise_column_cell_fill (Minecraft 1.20.1 world generation; tested with the vanilla
 * 1.20.1 overworld router and Tectonic 3.0.17's; both sides, it acts wherever terrain is generated). SRG member names.
 *
 * <p>Terrain height queries (NoiseBasedChunkGenerator.getBaseHeight, getBaseColumn and every other predicate walk, all
 * through iterateNoiseColumn m_224239_) build a NoiseChunk for a single cell column and walk it from the top. For every
 * cell the walk visits, NoiseChunk.selectCellYZ (m_188810_) fills the cell cache - the CacheAllInCell over
 * add(finalDensity, beardifier) that NoiseChunk's constructor creates - for all cellWidth x cellWidth x cellHeight positions
 * of the cell (64 in this pack), but the walk reads only the cellHeight positions of the one queried column: x and z
 * are fixed for the whole query (updateForX / updateForZ set inCellX = x mod cellWidth, inCellZ = z mod cellWidth before
 * every read), and the NoiseChunk is a local of iterateNoiseColumn. In this pack that fill was about a fifth of all
 * height-query time (a world-generation census, 2026-10-05).
 *
 * <p>For NoiseChunks iterateNoiseColumn builds (marked by ColumnCellFillMarkMixin) the fill now evaluates the same
 * cell-cache function at the queried column's positions only, top to bottom as vanilla visits them, through
 * {@link Column}, a context provider whose forIndex / fillAllDirectly set the NoiseChunk's in-cell position and array
 * index exactly as NoiseChunk.forIndex does for those positions and hand back the NoiseChunk itself; each value is
 * written into the slot vanilla writes it to. The other slots keep older values, which nothing reads.
 *
 * <p>Why every value read is the same: a value of the fill is a function of the position and of NoiseChunk state that
 * the fill does not change (interpolator corners, cellStart*, both counters, fillingCell), as long as the part of the
 * graph a cell fill evaluates consists of pure density functions, NoiseInterpolators (while filling a cell they return
 * Mth.lerp3 of the selected corners at the in-cell position and never evaluate below themselves), FlatCaches (every
 * position of the cell lies inside their grid, so they return the values computed in the constructor) and Cache2Ds whose
 * function reads only x and z and holds no NoiseChunk-state wrapper (their remembered value then equals a fresh
 * computation at any y, from any context). The cell provider never touches interpolationCounter or
 * arrayInterpolationCounter (vanilla's forIndex / fillAllDirectly do not either), so both counters after the fill equal
 * vanilla's. A CacheOnce inside the evaluated part would answer with the first position computed in the cell (the counter
 * does not move during a cell fill), and a smaller fill would change which position that is: such graphs are refused.
 * The in-cell fields and arrayIndex left behind are overwritten by updateForY / updateForX / updateForZ before anything
 * reads them; arrayIndex is read only by a CacheOnce array hit, which cannot happen in the walk (selectCellYZ leaves
 * arrayInterpolationCounter at a value no fill used).
 *
 * <p>The original code runs (per world generator, decided once on the first column NoiseChunk and logged at INFO) when
 * the evaluated part of the cell-cache graph holds a CacheOnce, another cell cache, a y-reading or state-reading Cache2D,
 * a cache of another NoiseChunk, a density function type DensityAudit does not know anywhere in the cell-cache graph,
 * when the NoiseChunk has more than one cell cache, a blender, more than one cell column, or when BetterEnd fills the
 * generator's slices itself. As a safety net, the first 256 column NoiseChunks of each generator and then one in 4096 are
 * filled both ways and compared bit for bit; the vanilla values are kept, and any difference logs a warning and switches
 * the generator back to the original code. Runtime switch -Dbons_and_furious.columnCellFill=false; shadow mode
 * -Dbons_and_furious.columnCellFill.shadow=true compares every cell fill (vanilla values kept), counts in SHADOW_CHECKS /
 * SHADOW_MISMATCHES and logs at most 20 mismatches.
 */
public final class ColumnCellFill {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NC = "net.minecraft.world.level.levelgen.NoiseChunk$";
    private static final long CANARY = 256, SAMPLE_MASK = 4095;
    private static final int MAX_SHADOW_WARNINGS = 20;

    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.columnCellFill", "true"));
    /** Shadow mode: every marked cell fill is computed both ways and compared; the vanilla values are kept. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.columnCellFill.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final boolean METRICS = Boolean.getBoolean("bons_and_furious.columnCellFill.metrics");
    /** Column NoiseChunks marked, cell fills served by the column, cell fills verified both ways, fills left to vanilla (metrics flag only). */
    public static final LongAdder MARKED = new LongAdder(), COLUMN_FILLS = new LongAdder(), VERIFIED_FILLS = new LongAdder(), BYPASSED = new LongAdder();

    private static final ConcurrentMap<RandomState, World> WORLDS = new MapMaker().weakKeys().concurrencyLevel(16).makeMap();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static volatile boolean announced;

    private ColumnCellFill() {
    }

    /** The NoiseChunk side, implemented by NoiseChunkColumnFillMixin. */
    public interface Cells {
        List<?> bons$cfCaches();

        int bons$cfWidth();

        int bons$cfHeight();

        int bons$cfCountXZ();

        Blender bons$cfBlender();

        NoiseSettings bons$cfNoiseSettings();

        /** Sets inCellX / inCellY / inCellZ and arrayIndex the way NoiseChunk.forIndex does and returns the NoiseChunk. */
        DensityFunction.FunctionContext bons$cfPosition(int inCellX, int inCellY, int inCellZ, int arrayIndex);

        Column bons$cfColumn();

        void bons$cfColumn(Column column);
    }

    /**
     * iterateNoiseColumn, right before the NoiseChunk it built starts interpolating: remember the queried column on it when
     * this generator's cell-cache graph qualifies. Never throws; leaves the NoiseChunk unmarked otherwise.
     */
    public static void mark(NoiseChunk chunk, NoiseBasedChunkGenerator generator, RandomState random, int x, int z) {
        if (!enabled || chunk == null || random == null) return;
        try {
            Cells cells = (Cells) (Object) chunk;
            if (cells.bons$cfCountXZ() != 1 || cells.bons$cfBlender() != Blender.m_190153_()) return;
            List<?> caches = cells.bons$cfCaches();
            if (caches.size() != 1 || !(caches.get(0) instanceof DensityFunctions.MarkerOrMarked cache)) return;
            DensityFunction filler = cache.m_207056_();
            World w = WORLDS.get(random);
            if (w == null) w = WORLDS.computeIfAbsent(random, r -> new World());
            NoiseGeneratorSettings settings = generator.m_224341_().m_203334_();
            Shape s = w.shape(settings, cells, filler);
            if (s == null || s.refusal != null || filler.getClass() != s.fillerClass) return;
            int cw = cells.bons$cfWidth(), ch = cells.bons$cfHeight();
            cells.bons$cfColumn(new Column(cells, s, filler, Math.floorMod(x, cw), Math.floorMod(z, cw), cw, ch, SHADOW || s.verifyNext()));
            if (METRICS) MARKED.increment();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_noise_column_cell_fill fills only the queried column of each cell in terrain height queries ({} of {} cell-cache positions per cell)",
                        ch, cw * cw * ch);
            }
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static void disable(Throwable t) {
        if (!enabled) return;
        enabled = false;
        LOGGER.warn("Bons and Furious: vanilla_noise_column_cell_fill switched itself off; world generation continues with the original code", t);
    }

    /** One RandomState: one decision per generator settings instance and noise shape (normally exactly one). */
    static final class World {
        private volatile Shape[] shapes = new Shape[0];

        Shape shape(NoiseGeneratorSettings settings, Cells cells, DensityFunction filler) {
            NoiseSettings noise = cells.bons$cfNoiseSettings();
            int cw = cells.bons$cfWidth(), ch = cells.bons$cfHeight();
            for (Shape s : shapes) if (s.matches(settings, noise, cw, ch)) return s;
            synchronized (this) {
                for (Shape s : shapes) if (s.matches(settings, noise, cw, ch)) return s;
                Shape s = new Shape(settings, noise, cw, ch, filler.getClass());
                String r;
                try {
                    r = betterEndTarget(settings);
                    if (r == null) r = audit(cells, filler);
                } catch (Throwable t) {
                    r = "audit failed: " + t;
                }
                if (r != null) s.refuse(r, false);
                Shape[] next = java.util.Arrays.copyOf(shapes, shapes.length + 1);
                next[shapes.length] = s;
                shapes = next;
                return s;
            }
        }
    }

    /** The decision for one generator settings instance, noise settings and cell size, plus its canary counter. */
    static final class Shape {
        final NoiseGeneratorSettings settings;
        final NoiseSettings noise;
        final int cellWidth, cellHeight;
        final Class<?> fillerClass;
        final AtomicLong marked = new AtomicLong();
        volatile String refusal;

        Shape(NoiseGeneratorSettings settings, NoiseSettings noise, int cellWidth, int cellHeight, Class<?> fillerClass) {
            this.settings = settings;
            this.noise = noise;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.fillerClass = fillerClass;
        }

        boolean matches(NoiseGeneratorSettings settings, NoiseSettings noise, int cw, int ch) {
            return this.settings == settings && this.noise.equals(noise) && cellWidth == cw && cellHeight == ch;
        }

        boolean verifyNext() {
            long n = marked.incrementAndGet();
            return n <= CANARY || (n & SAMPLE_MASK) == 0;
        }

        void refuse(String reason, boolean warn) {
            if (refusal != null) return;
            refusal = reason;
            if (warn) LOGGER.warn("Bons and Furious: vanilla_noise_column_cell_fill switched off for one world generator: {}", reason);
            else if (LOGGED.add(reason)) LOGGER.info("Bons and Furious: vanilla_noise_column_cell_fill keeps the original code for world generators with {}", reason);
        }
    }

    /**
     * The context provider of one marked NoiseChunk's cell fills: position k of the queried column (k = 0 at the top of
     * the cell) is in-cell (inCellX, cellHeight - 1 - k, inCellZ), array index ((k * cellWidth + inCellX) * cellWidth +
     * inCellZ), the order and slots of NoiseChunk.forIndex / fillAllDirectly.
     */
    public static final class Column implements DensityFunction.ContextProvider {
        private final Cells cells;
        private final Shape shape;
        private final DensityFunction filler;
        private final int inCellX, inCellZ, cellWidth, cellHeight;
        private final boolean verify;
        private final double[] column;

        Column(Cells cells, Shape shape, DensityFunction filler, int inCellX, int inCellZ, int cellWidth, int cellHeight, boolean verify) {
            this.cells = cells;
            this.shape = shape;
            this.filler = filler;
            this.inCellX = inCellX;
            this.inCellZ = inCellZ;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.verify = verify;
            this.column = new double[cellHeight];
        }

        private int slot(int k) {
            return (k * cellWidth + inCellX) * cellWidth + inCellZ;
        }

        /** True for the canary / shadow NoiseChunks, whose cells are filled both ways (the vanilla values are kept). */
        public boolean verifying() {
            return verify;
        }

        /** forIndex (m_207263_). */
        @Override
        public DensityFunction.FunctionContext m_207263_(int k) {
            return cells.bons$cfPosition(inCellX, cellHeight - 1 - k, inCellZ, slot(k));
        }

        /** fillAllDirectly (m_207207_): the column's positions, top to bottom. */
        @Override
        public void m_207207_(double[] values, DensityFunction function) {
            for (int k = 0; k < cellHeight; k++) values[k] = function.m_207386_(cells.bons$cfPosition(inCellX, cellHeight - 1 - k, inCellZ, slot(k)));
        }

        /** selectCellYZ's cell-cache fill (NoiseChunk's wrapped call): the column only, or both ways when verifying. */
        public void fill(DensityFunction function, double[] values, DensityFunction.ContextProvider provider, Operation<Void> original) {
            if (!enabled || function != filler || shape.refusal != null || values.length != cellWidth * cellWidth * cellHeight) {
                if (METRICS) BYPASSED.increment();
                original.call(function, values, provider);
                return;
            }
            double[] c = column;
            function.m_207362_(c, this);
            if (verify) {
                original.call(function, values, provider);           // the vanilla values are the ones kept
                if (METRICS) VERIFIED_FILLS.increment();
                if (SHADOW) SHADOW_CHECKS.incrementAndGet();
                for (int k = 0; k < cellHeight; k++) {
                    if (Double.doubleToRawLongBits(values[slot(k)]) != Double.doubleToRawLongBits(c[k])) {
                        mismatch(k, values[slot(k)], c[k]);
                        break;
                    }
                }
                return;
            }
            for (int k = 0; k < cellHeight; k++) values[slot(k)] = c[k];
            if (METRICS) COLUMN_FILLS.increment();
        }

        private void mismatch(int k, double full, double col) {
            String what = "a column value differed from the full cell fill (in-cell " + inCellX + "," + (cellHeight - 1 - k) + "," + inCellZ
                    + ": full " + full + ", column " + col + ")";
            if (SHADOW) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= MAX_SHADOW_WARNINGS) LOGGER.warn("Bons and Furious: vanilla_noise_column_cell_fill shadow mismatch {}: {}", n, what);
            } else {
                shape.refuse(what, true);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- the audit

    /** Null when a cell fill of this graph may evaluate only the queried column; otherwise the reason it may not. */
    static String audit(Cells owner, DensityFunction filler) throws ReflectiveOperationException {
        DensityAudit.Walk walk = new DensityAudit.Walk();
        walk.dependency(filler);
        if (walk.refusal != null) return walk.refusal + " in the cell-cache graph";
        return evaluated(filler, walk, owner, new IdentityHashMap<>());
    }

    /** Walks the part of the graph a cell fill evaluates (it stops at interpolators and flat caches). */
    private static String evaluated(Object node, DensityAudit.Walk walk, Object owner, IdentityHashMap<Object, Boolean> seen) throws ReflectiveOperationException {
        if (node == null || seen.put(node, Boolean.TRUE) != null) return null;
        String cn = node.getClass().getName();
        if (cn.startsWith(NC)) {
            switch (cn.substring(NC.length())) {
                case "NoiseInterpolator", "FlatCache" -> {
                    return ownerOf(node) == owner ? null : "a NoiseChunk cache of another NoiseChunk in the cell-cache graph";
                }
                case "Cache2D" -> {
                    if (walk.dependency(node) != DensityAudit.XZ) return "a y-reading Cache2D in the cell-cache graph";
                    return stateFree(node, new IdentityHashMap<>());
                }
                case "CacheOnce" -> {
                    return "a CacheOnce evaluated by the cell fill";
                }
                case "CacheAllInCell" -> {
                    return "a second cell cache evaluated by the cell fill";
                }
                case "BlendAlpha", "BlendOffset" -> {
                    return null;                                   // functions of (x, z) and the NoiseChunk's blender
                }
                default -> {
                    return "unsupported NoiseChunk type " + cn + " in the cell-cache graph";
                }
            }
        }
        for (Object child : children(node)) {
            String r = evaluated(child, walk, owner, seen);
            if (r != null) return r;
        }
        return null;
    }

    /** Null when nothing below a Cache2D depends on NoiseChunk interpolation or cell state. */
    private static String stateFree(Object node, IdentityHashMap<Object, Boolean> seen) throws ReflectiveOperationException {
        if (node == null || seen.put(node, Boolean.TRUE) != null) return null;
        String cn = node.getClass().getName();
        if (cn.equals(NC + "NoiseInterpolator") || cn.equals(NC + "CacheAllInCell") || cn.equals(NC + "CacheOnce"))
            return "a Cache2D over NoiseChunk interpolation or cell state in the cell-cache graph";
        for (Object child : children(node)) {
            String r = stateFree(child, seen);
            if (r != null) return r;
        }
        return null;
    }

    private static Object ownerOf(Object wrapper) throws ReflectiveOperationException {
        for (Field f : DensityAudit.fields(wrapper.getClass())) if (f.getType() == NoiseChunk.class) return f.get(wrapper);
        return null;
    }

    /** Density functions, splines and spline coordinates directly below a node (the walk DensityAudit does). */
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
        if (v instanceof DensityFunction || v instanceof CubicSpline<?, ?>) {
            out.add(v);
        } else if (v instanceof Holder<?> h) {
            Object x = h.m_203334_();
            if (x instanceof DensityFunction) out.add(x);
        } else if (v instanceof List<?> l) {
            for (Object e : l) add(out, e);
        } else if (v.getClass().getName().equals("net.minecraft.world.level.levelgen.DensityFunctions$Spline$Coordinate")) {
            out.add(v);
        }
    }

    /** BetterEnd fills the slices of its End NoiseChunks with its own code instead of the density graph. */
    private static String betterEndTarget(NoiseGeneratorSettings settings) {
        for (Class<?> c = settings.getClass(); c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                if (!i.getName().equals("org.betterx.betterend.interfaces.BETargetChecker")) continue;
                try {
                    Method m = i.getMethod("be_isTarget");
                    if (Boolean.TRUE.equals(m.invoke(settings))) return "BetterEnd filling this generator's slices itself";
                } catch (ReflectiveOperationException e) {
                    return "a failed BetterEnd target check (" + e + ")";
                }
                return null;
            }
        }
        return null;
    }
}
