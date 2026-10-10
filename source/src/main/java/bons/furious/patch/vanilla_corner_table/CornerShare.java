package bons.furious.patch.vanilla_corner_table;

import bons.pure.terrain.DensityAudit;
import com.google.common.collect.MapMaker;
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
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_noise_corner_share (Minecraft 1.21.1 world generation, tested build NeoForge 21.1.252;
 * on 1.20.1 tested with the vanilla overworld router and Tectonic 3.0.17's; both sides, it acts wherever terrain is
 * generated). Mojang member names.
 *
 * <p>Every terrain height query builds a NoiseChunk of one cell column and fills its four corner columns
 * (NoiseChunk.fillSlice, called by initializeForFirstCellX and advanceCellX): every NoiseInterpolator's function
 * at the cellCountY + 1 cell corners of each corner column (193 values each in this pack). That fill was 45% of all
 * height-query time, and 79% of the corner columns filled during generation had been filled before by another query
 * (a world-generation census, 2026-10-05: 103,346 repeats, every one bit-identical in values and in the interpolationCounter
 * increment). Neighbouring queries share corners: a 4 x 4 cell column shares its corners with eight others.
 *
 * <p>For one-cell NoiseChunks without a blender (the ones height queries and other column walks build) the slice fill
 * now runs through a table shared by all threads, per world generator: a direct-mapped array of immutable entries keyed by
 * the corner column. A hit copies the stored columns into the slices, sets interpolationCounter to the value the fill
 * would have left (its start plus the stored increment) and the slice provider's last cellStartBlockY / inCellY /
 * arrayIndex; arrayInterpolationCounter, cellStartBlockX/Z and inCellX/Z move exactly as in the original loop. A miss runs
 * the original fills and stores the result. Interpolators whose functions are small (under 16 evaluated nodes) are not
 * stored but recomputed on every hit, which keeps an entry at the size of the expensive ones.
 *
 * <p>Why a stored corner equals a fresh fill: the values and the counter increment of a corner fill are a function of the
 * corner, the world generator and the noise settings alone when the part of the interpolators' graphs a slice fill
 * evaluates consists of pure density functions, CacheOnce wrappers (their per-point cache is keyed by an
 * interpolationCounter value that every fill step increments first, their array cache by an arrayInterpolationCounter value
 * unique to the corner, so state from before the corner is never reused), FlatCaches (corner columns lie inside their grid,
 * so they answer with constructor values) and Cache2Ds whose function reads only x and z and holds no NoiseChunk-state
 * wrapper. What a hit leaves different - the state of those CacheOnce and Cache2D wrappers - is never observed: a Cache2D
 * of that kind answers any later read with the value a recomputation gives, and a CacheOnce state from a slice fill could
 * only answer the first cell fill (the one window in which no counter moves), which the audit rules out by refusing a
 * CacheOnce in the part of the cell-cache graph a cell fill evaluates; every later reader increments a counter first.
 *
 * <p>Refused (the original code runs; per world generator, decided once and logged at INFO): a density function type
 * DensityAudit does not know in the interpolators' or the cell cache's graphs; a nested NoiseInterpolator, a cell cache, a
 * y-reading or state-reading Cache2D, or a cache of another NoiseChunk in the evaluated part of an interpolator; a CacheOnce
 * in the evaluated part of the cell cache; NoiseChunks with more than one cell column or a blender; BetterEnd targets.
 * Safety net: the first 256 hits of each generator and then one in 4096 are filled the original way and compared with
 * the entry (stored values bit for bit, increment, provider state); the computed values are kept, and a difference logs a
 * warning and switches that generator back to the original code. Runtime switch -Dbons_and_furious.cornerShare=false;
 * shadow mode -Dbons_and_furious.cornerShare.shadow=true verifies every hit (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20
 * warnings). Memory: at most 4096 entries per world generator (in this pack 6.2 KB each, 25 MB when full; 392 bytes for the vanilla overworld).
 *
 * <p>Ported to 1.21.1: NoiseChunk (fillSlice, initializeForFirstCellX / advanceCellX, the slice provider NoiseChunk$1 with
 * its forIndex / fillAllDirectly, selectCellYZ, updateForZ, the constructor) and its NoiseInterpolator, CacheOnce,
 * Cache2D and FlatCache are the same as on 1.20.1 (decompiler style only), as is the set of vanilla density function
 * types, so the audit, the table and the canary are unchanged. Two 1.21.1 neighbours rewrite the code this switch replays
 * and it steps aside when either is installed (patches/vanilla_corner_table.json yields): Generator Accelerator 1.6.2
 * (MixinNoiseInterpolator @Overwrites the interpolator's selectCellYZ / swapSlices / compute and redirects how its slices
 * are allocated; its MixinNoiseChunk, listed in the same config, @Overwrites fillSlice) and C2ME 0.4's density-function
 * compiler (MixinChunkNoiseSamplerDensityInterpolator wraps NoiseInterpolator.compute; C2ME's code is not read here,
 * clean room).
 */
public final class CornerShare {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NC = "net.minecraft.world.level.levelgen.NoiseChunk$";
    private static final long CANARY = 256, SAMPLE_MASK = 4095;
    private static final int BITS = 12, SMALL = 16, MAX_SHADOW_WARNINGS = 20;

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cornerShare", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cornerShare.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final boolean METRICS = Boolean.getBoolean("bons_and_furious.cornerShare.metrics");
    /** Corner fills served from the table, filled and stored, verified both ways, fills left to the original (metrics only). */
    public static final LongAdder HITS = new LongAdder(), MISSES = new LongAdder(), VERIFIED = new LongAdder(), BYPASSED = new LongAdder();

    private static final ConcurrentMap<RandomState, World> WORLDS = new MapMaker().weakKeys().concurrencyLevel(16).makeMap();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    /** The decision for NoiseChunks that never qualify (more than one cell column, a blender, no captured world). */
    public static final Shape NONE = new Shape(null, null, 0, 0, null);
    private static volatile boolean announced;

    static {
        NONE.refusal = "not a one-cell NoiseChunk without a blender";
    }

    private CornerShare() {
    }

    /** The NoiseChunk side (NoiseChunkCornerShareMixin); only the decision inputs, the fill itself runs in the mixin. */
    public interface Chunk {
        List<?> bons$csInterpolators();

        List<?> bons$csCellCaches();

        NoiseSettings bons$csNoiseSettings();
    }

    /** The decision for a NoiseChunk (called once per NoiseChunk, at its first slice fill). Never throws. */
    public static Shape shape(Object chunk, RandomState random, NoiseGeneratorSettings settings) {
        if (!enabled || random == null || settings == null) return NONE;
        try {
            Chunk c = (Chunk) chunk;
            World w = WORLDS.get(random);
            if (w == null) w = WORLDS.computeIfAbsent(random, r -> new World());
            return w.shape(settings, c, chunk);
        } catch (Throwable t) {
            disable(t);
            return NONE;
        }
    }

    static void disable(Throwable t) {
        if (!enabled) return;
        enabled = false;
        LOGGER.warn("Bons and Furious: vanilla_noise_corner_share switched itself off; world generation continues with the original code", t);
    }

    static final class World {
        private volatile Shape[] shapes = new Shape[0];

        Shape shape(NoiseGeneratorSettings settings, Chunk c, Object chunk) {
            NoiseSettings noise = c.bons$csNoiseSettings();
            int n = c.bons$csInterpolators().size();
            for (Shape s : shapes) if (s.matches(settings, noise, n)) return s;
            synchronized (this) {
                for (Shape s : shapes) if (s.matches(settings, noise, n)) return s;
                boolean[] stored = new boolean[n];
                int[] nodes = new int[n];
                String r;
                try {
                    r = betterEndTarget(settings);
                    if (r == null) r = audit(c, chunk, stored, nodes);
                } catch (Throwable t) {
                    r = "audit failed: " + t;
                }
                Shape s = new Shape(settings, noise, n, Math.floorDiv(noise.height(), noise.getCellHeight()) + 1, stored);
                System.arraycopy(nodes, 0, s.nodes, 0, n);
                if (r != null) s.refuse(r, false);
                Shape[] next = java.util.Arrays.copyOf(shapes, shapes.length + 1);
                next[shapes.length] = s;
                shapes = next;
                return s;
            }
        }
    }

    /** One world generator's settings + noise shape: the decision, which interpolators are stored, and the table. */
    public static final class Shape {
        final NoiseGeneratorSettings settings;
        final NoiseSettings noise;
        final int interpolators, length;
        /** Interpolator indices whose columns entries store (the others are recomputed on a hit). */
        public final boolean[] stored;
        /** Per interpolator: the nodes a slice fill evaluates below it (the audit's count; stored when at least 16). */
        public final int[] nodes;
        private final AtomicReferenceArray<Entry> slots;
        private final AtomicLong hits = new AtomicLong();
        public volatile String refusal;

        Shape(NoiseGeneratorSettings settings, NoiseSettings noise, int interpolators, int length, boolean[] stored) {
            this.settings = settings;
            this.noise = noise;
            this.interpolators = interpolators;
            this.length = length;
            this.stored = stored;
            this.nodes = new int[interpolators];
            this.slots = stored == null ? null : new AtomicReferenceArray<>(1 << BITS);
        }

        boolean matches(NoiseGeneratorSettings settings, NoiseSettings noise, int n) {
            return this.settings == settings && this.noise.equals(noise) && interpolators == n;
        }

        /** True while the table may be used. */
        public boolean active() {
            return refusal == null && enabled;
        }

        private static int index(int x, int z) {
            long k = ((long) x << 32 ^ (z & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L;
            return (int) ((k ^ (k >>> 31)) >>> (64 - BITS));
        }

        /** The stored corner (x, z), or null. */
        public Entry get(int x, int z) {
            Entry e = slots.get(index(x, z));
            return e != null && e.x == x && e.z == z ? e : null;
        }

        public void put(Entry e) {
            if (refusal == null) slots.set(index(e.x, e.z), e);
            if (METRICS) MISSES.increment();
        }

        /** Whether this hit is filled the original way and compared instead of being served (canary / shadow). */
        public boolean verifyNext() {
            if (SHADOW) return true;
            long n = hits.incrementAndGet();
            return n <= CANARY || (n & SAMPLE_MASK) == 0;
        }

        /** A verified hit: compare the fresh fill with the entry. Returns true when they agree. */
        public boolean check(Entry e, double[][] fresh, long increment, int lastY, int lastInCellY, int lastIndex) {
            if (METRICS) VERIFIED.increment();
            if (SHADOW) SHADOW_CHECKS.incrementAndGet();
            String diff = null;
            if (e.increment != increment) diff = "interpolationCounter increment " + e.increment + " stored, " + increment + " computed";
            else if (e.lastY != lastY || e.lastInCellY != lastInCellY || e.lastIndex != lastIndex) diff = "slice provider state differs";
            else {
                for (int k = 0; k < fresh.length && diff == null; k++) {
                    if (e.columns[k] == null) continue;
                    for (int i = 0; i < length; i++) {
                        if (Double.doubleToRawLongBits(e.columns[k][i]) != Double.doubleToRawLongBits(fresh[k][i])) {
                            diff = "interpolator " + k + " value " + i + ": stored " + e.columns[k][i] + ", computed " + fresh[k][i];
                            break;
                        }
                    }
                }
            }
            if (diff == null) return true;
            String what = "a stored corner column differed from a fresh fill (corner " + e.x + "," + e.z + ", " + diff + ")";
            if (SHADOW) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= MAX_SHADOW_WARNINGS) LOGGER.warn("Bons and Furious: vanilla_noise_corner_share shadow mismatch {}: {}", m, what);
            } else {
                refuse(what, true);
            }
            return false;
        }

        public void served() {
            if (METRICS) HITS.increment();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_noise_corner_share shares the corner columns of terrain height queries between queries ({} of {} interpolators stored per corner)",
                        count(stored), interpolators);
            }
        }

        void refuse(String reason, boolean warn) {
            if (refusal != null) return;
            refusal = reason;
            if (warn) LOGGER.warn("Bons and Furious: vanilla_noise_corner_share switched off for one world generator: {}", reason);
            else if (LOGGED.add(reason)) LOGGER.info("Bons and Furious: vanilla_noise_corner_share keeps the original code for world generators with {}", reason);
        }
    }

    private static int count(boolean[] b) {
        int n = 0;
        if (b != null) for (boolean x : b) if (x) n++;
        return n;
    }

    /** One corner column: the stored interpolators' values (null for recomputed ones), the counter increment, provider state. */
    public static final class Entry {
        public final int x, z;
        public final double[][] columns;
        public final long increment;
        public final int lastY, lastInCellY, lastIndex;

        public Entry(int x, int z, double[][] columns, long increment, int lastY, int lastInCellY, int lastIndex) {
            this.x = x;
            this.z = z;
            this.columns = columns;
            this.increment = increment;
            this.lastY = lastY;
            this.lastInCellY = lastInCellY;
            this.lastIndex = lastIndex;
        }
    }

    // ------------------------------------------------------------------------------------------------- the audit

    /** Null when a corner fill of this NoiseChunk's interpolators is a function of the corner; fills {@code stored}. */
    static String audit(Chunk c, Object chunk, boolean[] stored, int[] counts) throws ReflectiveOperationException {
        List<?> interpolators = c.bons$csInterpolators();
        DensityAudit.Walk walk = new DensityAudit.Walk();
        for (int k = 0; k < interpolators.size(); k++) {
            Object interp = interpolators.get(k);
            if (!interp.getClass().getName().equals(NC + "NoiseInterpolator") || ownerOf(interp) != chunk)
                return "an interpolator that is not this NoiseChunk's";
            DensityFunction f = ((DensityFunctions.MarkerOrMarked) interp).wrapped();
            walk.dependency(f);
            if (walk.refusal != null) return walk.refusal + " in an interpolated function";
            int[] nodes = {0};
            String r = sliceEvaluated(f, walk, chunk, new IdentityHashMap<>(), nodes);
            if (r != null) return r;
            stored[k] = nodes[0] >= SMALL;
            counts[k] = nodes[0];
        }
        List<?> caches = c.bons$csCellCaches();
        for (Object cache : caches) {
            DensityFunction f = ((DensityFunctions.MarkerOrMarked) cache).wrapped();
            walk.dependency(f);
            if (walk.refusal != null) return walk.refusal + " in the cell-cache graph";
            String r = cellEvaluated(f, chunk, new IdentityHashMap<>());
            if (r != null) return r;
        }
        return null;
    }

    /** What a slice fill evaluates below an interpolator: it stops at flat caches (constructor values at the corners). */
    private static String sliceEvaluated(Object node, DensityAudit.Walk walk, Object owner, IdentityHashMap<Object, Boolean> seen, int[] nodes)
            throws ReflectiveOperationException {
        if (node == null || seen.put(node, Boolean.TRUE) != null) return null;
        nodes[0]++;
        String cn = node.getClass().getName();
        if (cn.startsWith(NC)) {
            switch (cn.substring(NC.length())) {
                case "FlatCache" -> {
                    return ownerOf(node) == owner ? null : "a NoiseChunk cache of another NoiseChunk in an interpolated function";
                }
                case "CacheOnce" -> {
                    if (ownerOf(node) != owner) return "a NoiseChunk cache of another NoiseChunk in an interpolated function";
                }
                case "Cache2D" -> {
                    if (walk.dependency(node) != DensityAudit.XZ) return "a y-reading Cache2D evaluated by the slice fill";
                    return stateFree(node, new IdentityHashMap<>());
                }
                case "NoiseInterpolator" -> {
                    return "a NoiseInterpolator inside an interpolated function";
                }
                case "CacheAllInCell" -> {
                    return "a cell cache inside an interpolated function";
                }
                default -> {
                    return "NoiseChunk type " + cn + " inside an interpolated function";
                }
            }
        }
        for (Object child : children(node)) {
            String r = sliceEvaluated(child, walk, owner, seen, nodes);
            if (r != null) return r;
        }
        return null;
    }

    /** The part of the cell-cache graph a cell fill evaluates must hold no CacheOnce (see the class documentation). */
    private static String cellEvaluated(Object node, Object owner, IdentityHashMap<Object, Boolean> seen) throws ReflectiveOperationException {
        if (node == null || seen.put(node, Boolean.TRUE) != null) return null;
        String cn = node.getClass().getName();
        if (cn.startsWith(NC)) {
            switch (cn.substring(NC.length())) {
                case "NoiseInterpolator", "FlatCache", "Cache2D" -> {
                    return null;
                }
                case "CacheOnce" -> {
                    return "a CacheOnce evaluated by the cell fill";
                }
                default -> {
                    return "NoiseChunk type " + cn + " evaluated by the cell fill";
                }
            }
        }
        for (Object child : children(node)) {
            String r = cellEvaluated(child, owner, seen);
            if (r != null) return r;
        }
        return null;
    }

    private static String stateFree(Object node, IdentityHashMap<Object, Boolean> seen) throws ReflectiveOperationException {
        if (node == null || seen.put(node, Boolean.TRUE) != null) return null;
        String cn = node.getClass().getName();
        if (cn.equals(NC + "NoiseInterpolator") || cn.equals(NC + "CacheAllInCell") || cn.equals(NC + "CacheOnce"))
            return "a Cache2D over NoiseChunk interpolation or cell state in an interpolated function";
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
            Object x = h.value();
            if (x instanceof DensityFunction) out.add(x);
        } else if (v instanceof List<?> l) {
            for (Object e : l) add(out, e);
        } else if (v.getClass().getName().equals("net.minecraft.world.level.levelgen.DensityFunctions$Spline$Coordinate")) {
            out.add(v);
        }
    }

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
