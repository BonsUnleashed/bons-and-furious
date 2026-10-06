package bons.pure.terrain;

import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.slf4j.Logger;

/**
 * Bons and Furious switch terrain_surface_estimate_share (vanilla 1.20.1 world generation). SRG member names.
 *
 * NoiseChunk.computePreliminarySurfaceLevel(column) (m_198249_) scans the initial density from max_y down in
 * cell-height steps until it exceeds 0.390625; aquifers and surface rules use it. Each NoiseChunk (one per generated
 * chunk and one per single-column height query, which structure placement issues constantly) keeps its own table, so
 * neighbouring NoiseChunks scan the same columns again: about 25 times per column while generating this pack.
 *
 * The result depends only on the RandomState, the noise settings and the column when all of these hold, and only
 * then is it shared between NoiseChunks (otherwise the original code runs):
 *   1. the NoiseChunk has no terrain blending (its Blender is Blender.empty());
 *   2. every flat_cache / cache_2d subtree of its initial density reads only x and z: inside a NoiseChunk those are
 *      read from values computed at y = 0, outside it at the real y, and the two agree only for x/z-only functions;
 *   3. the graph contains only the vanilla density function types below: other types (mod functions) may carry
 *      per-chunk state, e.g. Bumblezone's biome noise bound to each NoiseChunk. 1.0.34: and no beardifier, which is
 *      the NoiseChunk's own (the structure pieces near its chunk; in a height query's NoiseChunk the marker, 0).
 * Conditions 2 and 3 are audited once per RandomState on its first NoiseChunk; a failure is logged and keeps that
 * RandomState on the original code.
 *
 * Storage: per RandomState and noise settings, a bounded lock-free direct-mapped table of immutable (column, value)
 * entries (2^16 slots, about 2 MB when full). Two threads racing on one column both compute and store the same value.
 * The RandomState is identified by its aquifer PositionalRandomFactory by IDENTITY (the factory is a value record whose
 * equality depends only on the seed, so equality-based keys would merge dimensions that share a seed); the weak-keyed
 * map lets unloaded worlds be collected.
 */
public final class SurfaceEstimateShare {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Returned by {@link #get} when the column is not shared or not present. The vanilla scan never returns it. */
    public static final int MISSING = Integer.MIN_VALUE;
    private static final int BITS = 16;

    /** Runtime switch (the config switch acts when classes are transformed). -Dbons.pure.surfaceEstimateShare=false also turns it off. */
    static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.surfaceEstimateShare", "true"));
    static final boolean METRICS = Boolean.getBoolean("bons_and_furious.surfaceEstimateShare.metrics");
    /** Shared hits, shared misses (then computed and stored) and bypassed calls (only with -Dbons.pure.surfaceEstimateShare.metrics=true). */
    public static final LongAdder HITS = new LongAdder(), MISSES = new LongAdder(), BYPASSED = new LongAdder();

    private static final MethodHandle BLENDER, AQUIFER, FACTORY, SETTINGS, CELL_HEIGHT, INITIAL;
    private static final Class<?> NOISE_BASED_AQUIFER;
    static final boolean READY;
    private static final ConcurrentMap<PositionalRandomFactory, World> WORLDS = new MapMaker().weakKeys().concurrencyLevel(16).makeMap();
    private static final ThreadLocal<Object[]> LAST = ThreadLocal.withInitial(() -> new Object[2]);

    static {
        MethodHandle[] h = new MethodHandle[6];
        Class<?> aquifer = null;
        boolean ready = false;
        try {
            aquifer = Class.forName("net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer", false, NoiseChunk.class.getClassLoader());
            h[0] = field(NoiseChunk.class, "f_188731_", Blender.class);                   // blender
            h[1] = field(NoiseChunk.class, "f_188728_", Aquifer.class);                   // aquifer
            h[2] = field(aquifer, "f_188410_", PositionalRandomFactory.class);           // NoiseBasedAquifer.positionalRandomFactory
            h[3] = field(NoiseChunk.class, "f_188717_", NoiseSettings.class);             // noiseSettings
            h[4] = field(NoiseChunk.class, "f_209171_", int.class);                      // cellHeight
            h[5] = field(NoiseChunk.class, "f_209162_", DensityFunction.class);           // initialDensityNoJaggedness
            ready = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: terrain_surface_estimate_share is inactive because NoiseChunk is not the supported 1.20.1 layout ({})", t.toString());
        }
        NOISE_BASED_AQUIFER = aquifer;
        BLENDER = h[0]; AQUIFER = h[1]; FACTORY = h[2]; SETTINGS = h[3]; CELL_HEIGHT = h[4]; INITIAL = h[5];
        READY = ready;
    }

    private SurfaceEstimateShare() {
    }

    private static MethodHandle field(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(name);
        if (f.getType() != type || Modifier.isStatic(f.getModifiers())) throw new NoSuchFieldException(owner.getName() + '.' + name + " is not the expected field");
        f.setAccessible(true);
        return MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(type, Object.class));
    }

    /** Coremod entry at the start of NoiseChunk.computePreliminarySurfaceLevel: the shared value, or {@link #MISSING}. */
    public static int get(NoiseChunk chunk, long column) {
        Table t = table(chunk);
        if (t == null) {
            if (METRICS) BYPASSED.increment();
            return MISSING;
        }
        int v = t.get(column);
        if (METRICS) (v == MISSING ? MISSES : HITS).increment();
        return v;
    }

    /** Coremod entry before each return of NoiseChunk.computePreliminarySurfaceLevel: stores and returns the value. */
    public static int put(NoiseChunk chunk, long column, int value) {
        Table t = table(chunk);
        if (t != null) t.put(column, value);
        return value;
    }

    private static Table table(NoiseChunk chunk) {
        if (!READY || !enabled) return null;
        try {
            if ((Blender) BLENDER.invokeExact((Object) chunk) != Blender.m_190153_()) return null;
            Aquifer aquifer = (Aquifer) AQUIFER.invokeExact((Object) chunk);
            if (aquifer == null || aquifer.getClass() != NOISE_BASED_AQUIFER) return null;
            PositionalRandomFactory factory = (PositionalRandomFactory) FACTORY.invokeExact((Object) aquifer);
            // Per-thread shortcut for the common case (one world); weak so an unloaded world's tables can be collected.
            Object[] last = LAST.get();
            World w = null;
            if (last[0] instanceof WeakReference<?> seen && seen.get() == factory) w = (World) ((WeakReference<?>) last[1]).get();
            if (w == null) {
                w = WORLDS.get(factory);
                if (w == null) w = WORLDS.computeIfAbsent(factory, k -> new World());
                last[0] = new WeakReference<>(factory);
                last[1] = new WeakReference<>(w);
            }
            if (!w.audited) w.audit((DensityFunction) INITIAL.invokeExact((Object) chunk));
            if (w.refusal != null) return null;
            NoiseSettings s = (NoiseSettings) SETTINGS.invokeExact((Object) chunk);
            return w.table(s.f_158688_(), s.f_64508_(), (int) CELL_HEIGHT.invokeExact((Object) chunk));
        } catch (Throwable t) {
            enabled = false;
            LOGGER.warn("Bons and Furious: terrain_surface_estimate_share switched itself off ({})", t.toString());
            return null;
        }
    }

    /** One RandomState: audit result plus one table per noise settings (normally exactly one). */
    static final class World {
        volatile boolean audited;
        volatile String refusal;
        private volatile Table[] tables = new Table[0];

        synchronized void audit(DensityFunction initialDensity) {
            if (audited) return;
            String r;
            try {
                r = new Audit().refusal(initialDensity);
            } catch (Throwable t) {
                r = "audit failed: " + t;
            }
            refusal = r;
            audited = true;
            if (r != null) LOGGER.info("Bons and Furious: terrain_surface_estimate_share keeps the original code for one world generator ({})", r);
        }

        Table table(int minY, int height, int cellHeight) {
            for (Table t : tables) if (t.minY == minY && t.height == height && t.cellHeight == cellHeight) return t;
            synchronized (this) {
                for (Table t : tables) if (t.minY == minY && t.height == height && t.cellHeight == cellHeight) return t;
                Table[] next = java.util.Arrays.copyOf(tables, tables.length + 1);
                next[tables.length] = new Table(minY, height, cellHeight);
                tables = next;
                return next[tables.length - 1];
            }
        }
    }

    record Entry(long column, int value) {
    }

    static final class Table {
        final int minY, height, cellHeight;
        private final AtomicReferenceArray<Entry> slots = new AtomicReferenceArray<>(1 << BITS);

        Table(int minY, int height, int cellHeight) {
            this.minY = minY;
            this.height = height;
            this.cellHeight = cellHeight;
        }

        private static int index(long column) {
            long z = column * 0x9E3779B97F4A7C15L;
            z = (z ^ (z >>> 29)) * 0xBF58476D1CE4E5B9L;
            return (int) ((z ^ (z >>> 32)) >>> (64 - BITS));
        }

        int get(long column) {
            Entry e = slots.get(index(column));
            return e != null && e.column() == column ? e.value() : MISSING;
        }

        void put(long column, int value) {
            if (value == MISSING) return;
            slots.set(index(column), new Entry(column, value));
        }
    }

    /**
     * Conditions 2 and 3: returns null when sharing is exact for this graph, otherwise the reason. Records are read
     * through their fields (record accessors do not resolve on the SRG runtime).
     */
    static final class Audit {
        private static final int XZ = 0, Y = 1;
        private static final String DFS = "net.minecraft.world.level.levelgen.DensityFunctions$", NC = "net.minecraft.world.level.levelgen.NoiseChunk$";
        private static final Set<String> COMPOSITE = Set.of(DFS + "Ap2", DFS + "MulOrAdd", DFS + "Mapped", DFS + "Clamp", DFS + "RangeChoice", DFS + "Spline",
                DFS + "Spline$Coordinate", DFS + "BlendDensity", DFS + "HolderHolder", DFS + "Marker", NC + "NoiseInterpolator", NC + "FlatCache", NC + "Cache2D",
                NC + "CacheOnce", NC + "CacheAllInCell", "net.minecraft.util.CubicSpline$Multipoint");
        private static final Set<String> XZ_LEAF = Set.of(DFS + "ShiftA", DFS + "ShiftB", DFS + "EndIslandDensityFunction", DFS + "Constant", DFS + "BlendAlpha",
                DFS + "BlendOffset", NC + "BlendAlpha", NC + "BlendOffset", "net.minecraft.util.CubicSpline$Constant");
        /** Types that read y; 1.0.34: their inputs are audited too (WeirdScaledSampler has one). */
        private static final Set<String> Y_LEAF = Set.of(DFS + "YClampedGradient", DFS + "WeirdScaledSampler", DFS + "Shift",
                "net.minecraft.world.level.levelgen.synth.BlendedNoise");
        /** 1.0.34: the beardifier marker and the beardifier it resolves to in each NoiseChunk; never shared (refused). */
        private static final Set<String> BEARDIFIER = Set.of(DFS + "BeardifierMarker", "net.minecraft.world.level.levelgen.Beardifier");
        private final Map<Object, Integer> memo = new IdentityHashMap<>();
        private String refusal;

        String refusal(DensityFunction root) throws Exception {
            dep(root);
            return refusal;
        }

        private int dep(Object o) throws Exception {
            if (o == null || refusal != null) return XZ;
            Integer m = memo.get(o);
            if (m != null) return m;
            memo.put(o, XZ);                                 // density graphs are DAGs; guards against surprises
            int d = compute(o);
            memo.put(o, d);
            return d;
        }

        private int compute(Object o) throws Exception {
            String cn = o.getClass().getName();
            if (XZ_LEAF.contains(cn)) return XZ;
            if (BEARDIFIER.contains(cn)) {                         // 1.0.34: was accepted as a Y leaf
                if (refusal == null) refusal = "the initial density reads the chunk's beardifier";
                return Y;
            }
            if (Y_LEAF.contains(cn)) {
                childrenMax(o);                                    // 1.0.34: the input of a WeirdScaledSampler was not audited
                return Y;
            }
            if (cn.equals(DFS + "Noise")) {
                double[] d = doubles(o);                           // xzScale, yScale
                return d.length == 2 && d[1] == 0.0 ? XZ : Y;
            }
            if (cn.equals(DFS + "ShiftedNoise")) {
                double[] d = doubles(o);                           // xzScale, yScale
                int children = childrenMax(o);                     // shiftX, shiftY, shiftZ
                return d.length == 2 && d[1] == 0.0 ? children : Y;
            }
            if (COMPOSITE.contains(cn)) {
                int d = childrenMax(o);
                if (d != XZ && flat(o, cn) && refusal == null) refusal = "a flat_cache/cache_2d reads y (" + cn + ")";
                return d;
            }
            if (refusal == null) refusal = "unsupported density function type " + cn;
            return Y;
        }

        private static boolean flat(Object o, String cn) throws Exception {
            if (cn.equals(NC + "FlatCache") || cn.equals(NC + "Cache2D")) return true;
            if (cn.equals(DFS + "Marker")) {
                for (Field f : fields(o.getClass())) {
                    if (f.getType().isEnum()) {
                        String t = ((Enum<?>) f.get(o)).name();
                        return t.equals("FlatCache") || t.equals("Cache2D");
                    }
                }
            }
            return false;
        }

        private int childrenMax(Object o) throws Exception {
            int d = XZ;
            for (Field f : fields(o.getClass())) {
                if (f.getType().isPrimitive()) continue;
                d = Math.max(d, value(f.get(o)));
            }
            return d;
        }

        private int value(Object v) throws Exception {
            if (v == null) return XZ;
            if (v instanceof DensityFunction || v instanceof CubicSpline<?, ?>) return dep(v);
            if (v instanceof Holder<?> h) {
                Object x = h.m_203334_();
                return x instanceof DensityFunction ? dep(x) : XZ;
            }
            if (v instanceof List<?> l) {
                int d = XZ;
                for (Object e : l) d = Math.max(d, value(e));
                return d;
            }
            if (v.getClass().getName().equals(DFS + "Spline$Coordinate")) return dep(v);
            return XZ;                                               // enums, noise holders, arrays, the owning NoiseChunk
        }

        private static double[] doubles(Object o) throws Exception {
            List<Double> out = new ArrayList<>();
            for (Field f : fields(o.getClass())) if (f.getType() == double.class) out.add(f.getDouble(o));
            double[] r = new double[out.size()];
            for (int i = 0; i < r.length; i++) r[i] = out.get(i);
            return r;
        }

        private static List<Field> fields(Class<?> c) {
            List<Class<?>> chain = new ArrayList<>();
            for (Class<?> k = c; k != null && k != Object.class && k != Record.class; k = k.getSuperclass()) chain.add(0, k);
            List<Field> out = new ArrayList<>();
            for (Class<?> k : chain) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    out.add(f);
                }
            }
            return out;
        }
    }
}
