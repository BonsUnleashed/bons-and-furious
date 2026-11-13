package bons.furious.patch.flat_cache_columns;

import bons.pure.terrain.DensityAudit;
import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_flat_cache_outside_columns (vanilla 1.20.1 world generation). SRG member names.
 *
 * A NoiseChunk keeps each flat_cache of the noise router as a small grid over its own chunk (NoiseChunk$FlatCache:
 * values for its quart columns, computed at y = 0). FlatCache.compute (m_207386_) reads the grid for a position inside
 * it and calls the wrapped function for any other position, every time: nothing is remembered. Three callers ask
 * outside the grid constantly: the aquifer's preliminary surface scans (one column, top of the world down in
 * cell-height steps: the same column-constant noise dozens of times), Alex's Caves' climate sample at the centre of
 * its rare-biome cell (the same far column for every biome lookup of a chunk), and YUNG's Cave Biomes' biome lookups
 * at the aquifer's fluid positions.
 *
 * With the switch a FlatCache keeps the values it computed outside its grid in a small table keyed by block x and z
 * and answers a repeated column from it. That is exact when the wrapped function is a function of x and z alone.
 * The check, once per noise router (one per RandomState):
 *   1. every density function of the router is a type known to be pure (DensityAudit's list, the rule the other
 *      terrain switches use for the whole router): such types hold their children in plain fields, so the check sees
 *      every flat_cache marker of the router;
 *   2. per flat_cache marker: it gets tables when it wraps a function that reads only x and z (every leaf is an x/z
 *      leaf, noises have y scale 0) and holds no interpolated / cache_all_in_cell marker (those read the NoiseChunk's
 *      cell position). A marker whose function reads y keeps the original code: outside the grid the game evaluates
 *      it at the request's own height (Tectonic 3's mountain ridges are such a flat cache: a noise with y scale 1).
 * Which marker a FlatCache was made from is told by the marker itself: the marker record's mapAll (the interface's
 * method, called unchanged) hands its result, when that is a FlatCache, the marker it came from
 * (bons.furious.mixin.flat_cache_columns.MarkerOriginMixin). A FlatCache uses a table when it was made while its
 * NoiseChunk's constructor wrapped the checked router, its marker passed rule 2, and the request's context is not the
 * NoiseChunk itself. Every other FlatCache (the two blend caches, one made later, one whose marker is unknown, one
 * made by another mod's code) keeps the original code. The key is the block position, not the quart: outside the grid
 * the wrapped function is evaluated at the exact position (inside it the grid holds quart-corner values, which this
 * switch never touches).
 *
 * The first 256 answers of every router, then one in 4096 per table, are recomputed and compared bit for bit; a
 * difference switches that router back to the original code for good.
 * -Dbons_and_furious.flatCacheColumns=false keeps the original code; .verify=true recomputes and compares every
 * answer (rigs); .metrics=true counts and prints a summary when the JVM exits.
 */
public final class OutsideColumns {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean ACTIVE = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.flatCacheColumns", "true"));
    static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.flatCacheColumns.verify");
    static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.flatCacheColumns.metrics");
    public static final LongAdder SERVED = new LongAdder(), COMPUTED = new LongAdder(), UNTABLED = new LongAdder(), COMPARED = new LongAdder(),
            MISMATCHED = new LongAdder(), TABLES = new LongAdder(), KEPT = new LongAdder(), UNLINKED = new LongAdder();
    private static final String DFS = "net.minecraft.world.level.levelgen.DensityFunctions$";
    private static final String MARKER = DFS + "Marker";
    private static final int CANARY = 256;

    private static final ConcurrentMap<NoiseRouter, World> WORLDS = new MapMaker().weakKeys().concurrencyLevel(8).makeMap();
    private static final ThreadLocal<Object[]> LAST = ThreadLocal.withInitial(() -> new Object[2]);
    private static volatile boolean noRouterSaid, unlinkedSaid;

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println(summary()), "Bons and Furious flat cache summary"));
        }
    }

    private OutsideColumns() {
    }

    /** Implemented by NoiseChunk through the mixin. */
    public interface Chunk {
        /** The audited world of the router this NoiseChunk wrapped, or null (not decided, switched off, refused). */
        World bons$outsideWorld();

        /** True while the NoiseChunk's constructor is inside its router.mapAll(wrap) call. */
        boolean bons$outsideWrapping();

        /** True once the NoiseChunk's constructor has returned (the verdict is taken then). */
        boolean bons$outsideDecided();

        /** cellCountXZ: 1 for a one-column NoiseChunk (height queries), 4 for a chunk's. */
        int bons$outsideCells();
    }

    /** Implemented by NoiseChunk$FlatCache through the mixin. */
    public interface Flat {
        /** The flat_cache marker record whose mapAll returned this FlatCache; the first one stays. */
        void bons$origin(Object marker);
    }

    public static String summary() {
        return "Bons and Furious vanilla_flat_cache_outside_columns: served " + SERVED.sum() + ", computed " + COMPUTED.sum() + ", without a table "
                + UNTABLED.sum() + ", tables " + TABLES.sum() + ", flat caches left alone because their function reads y or is not known " + KEPT.sum()
                + ", without a known marker " + UNLINKED.sum() + ", compared " + COMPARED.sum() + ", differences " + MISMATCHED.sum();
    }

    /** Lets a caller pass on whatever a method handle threw, unchanged. */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
        throw (T) t;
    }

    /** NoiseChunk's constructor, just before it wraps its router: the world of that router when it passed the check, else null. */
    public static World world(NoiseRouter router) {
        if (!ACTIVE || router == null) return null;
        Object[] last = LAST.get();
        World w = null;
        if (last[0] instanceof WeakReference<?> seen && seen.get() == router) w = (World) ((WeakReference<?>) last[1]).get();
        if (w == null) {
            w = WORLDS.get(router);
            if (w == null) {
                World made = new World(router);
                World raced = WORLDS.putIfAbsent(router, made);
                w = raced != null ? raced : made;
                if (raced == null) made.announce();         // several threads may check one router at once: one line
            }
            last[0] = new WeakReference<>(router);
            last[1] = new WeakReference<>(w);
        }
        return w.ok ? w : null;
    }

    /** One noise router (one RandomState): the verdict per flat_cache marker and the comparison budget. */
    public static final class World {
        volatile boolean ok;
        volatile int canary = CANARY;
        /** The router's flat_cache markers (by identity) whose function reads x and z alone; never changed after the constructor. */
        private final IdentityHashMap<Object, Boolean> tabled = new IdentityHashMap<>();
        private final String refusal;
        private final int kept;

        World(NoiseRouter router) {
            String why;
            int[] left = {0};
            try {
                why = audit(router, tabled, left);
            } catch (Throwable t) {
                why = "check failed: " + t;
            }
            if (why != null) tabled.clear();
            refusal = why;
            kept = left[0];
            ok = why == null && !tabled.isEmpty();
        }

        void announce() {
            if (refusal != null) {
                LOGGER.info("Bons and Furious: vanilla_flat_cache_outside_columns keeps the original code for one world generator ({})", refusal);
            } else if (!tabled.isEmpty()) {
                LOGGER.info("Bons and Furious: vanilla_flat_cache_outside_columns remembers columns outside the grid for one world generator: {} of its {} "
                        + "flat caches read x and z alone{}", tabled.size(), tabled.size() + kept,
                        kept == 0 ? "" : "; the other " + kept + " read y or hold a cell marker and keep the original code");
            } else if (kept > 0) {
                LOGGER.info("Bons and Furious: vanilla_flat_cache_outside_columns keeps the original code for one world generator (none of its {} flat "
                        + "caches reads x and z alone)", kept);
            }
        }

        /** False once a comparison failed (or the check refused the router): its tables stop answering. */
        public boolean ok() {
            return ok;
        }

        /** True for a flat_cache marker of this router that passed the per-marker check. */
        boolean tables(Object marker) {
            return tabled.containsKey(marker);
        }

        void differs(double original, double served, int x, int z) {
            if (!ok) return;
            ok = false;
            LOGGER.warn("Bons and Furious: vanilla_flat_cache_outside_columns switched off for one world generator: a remembered column differed "
                    + "from the original evaluation at {},{} ({} vs {})", x, z, original, served);
        }
    }

    /**
     * Null when every density function of the router is a known pure type; else the reason. Fills {@code tabled} with
     * the flat_cache markers that wrap an x/z-only function of known pure types without a cell marker, and counts the
     * other flat_cache markers in {@code left}.
     */
    static String audit(NoiseRouter router, IdentityHashMap<Object, Boolean> tabled, int[] left) throws ReflectiveOperationException {
        List<Object> roots = new ArrayList<>();
        for (Field f : DensityAudit.fields(router.getClass())) {
            Object v = f.get(router);
            if (v instanceof DensityFunction) roots.add(v);
        }
        if (roots.isEmpty()) return "the noise router holds no density function";
        String unknown = DensityAudit.refusal(roots.toArray());
        if (unknown != null) return unknown;
        DensityAudit.Walk walk = new DensityAudit.Walk();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Object> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            Object o = todo.poll();
            if (seen.put(o, Boolean.TRUE) != null) continue;
            if (o.getClass().getName().equals(MARKER) && "FlatCache".equals(markerType(o))) {
                Object wrapped = markerWrapped(o);
                if (wrapped == null) return "a flat_cache marker without a function";
                if (walk.dependency(wrapped) == DensityAudit.XZ && walk.clean(wrapped) && cellMarker(wrapped) == null) tabled.put(o, Boolean.TRUE);
                else left[0]++;
            }
            children(o, todo);
        }
        return null;
    }

    /** "Interpolated" or "CacheAllInCell" when such a marker sits below o, else null. */
    private static String cellMarker(Object root) throws ReflectiveOperationException {
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Object> todo = new ArrayDeque<>();
        todo.add(root);
        while (!todo.isEmpty()) {
            Object o = todo.poll();
            if (seen.put(o, Boolean.TRUE) != null) continue;
            if (o.getClass().getName().equals(MARKER)) {
                String type = markerType(o);
                if ("Interpolated".equals(type) || "CacheAllInCell".equals(type)) return type;
            }
            children(o, todo);
        }
        return null;
    }

    private static String markerType(Object marker) throws ReflectiveOperationException {
        for (Field f : DensityAudit.fields(marker.getClass())) {
            if (f.get(marker) instanceof Enum<?> e) return e.name();
        }
        return null;
    }

    private static Object markerWrapped(Object marker) throws ReflectiveOperationException {
        for (Field f : DensityAudit.fields(marker.getClass())) {
            if (f.get(marker) instanceof DensityFunction d) return d;
        }
        return null;
    }

    /** Density functions, splines and spline coordinates directly below a node (holders are looked through), as DensityAudit walks them. */
    private static void children(Object o, ArrayDeque<Object> out) throws ReflectiveOperationException {
        for (Field f : DensityAudit.fields(o.getClass())) {
            if (f.getType().isPrimitive()) continue;
            add(out, f.get(o));
        }
    }

    private static void add(ArrayDeque<Object> out, Object v) {
        if (v == null) return;
        if (v instanceof DensityFunction || v instanceof CubicSpline<?, ?>) {
            out.add(v);
        } else if (v instanceof Holder<?> h) {
            Object x = h.m_203334_();
            if (x instanceof DensityFunction) out.add(x);
        } else if (v instanceof List<?> l) {
            for (Object e : l) add(out, e);
        } else if (v.getClass().getName().equals(DFS + "Spline$Coordinate")) {
            out.add(v);
        }
    }

    /** A FlatCache's table of columns outside its grid: direct-mapped, a newer column replaces an older one in its slot. */
    public static final class Table {
        /** Shared marker: this FlatCache keeps the original code. */
        public static final Table NONE = new Table(null, 0);
        public final World world;
        private final int mask;
        private final int[] xs, zs;
        private final double[] values;
        private long used;
        private int served;

        Table(World world, int slots) {
            this.world = world;
            this.mask = slots - 1;
            this.xs = new int[slots];
            this.zs = new int[slots];
            this.values = new double[slots];
        }

        public int slot(int x, int z) {
            int h = x * 0x9E3779B1 + z * 0x85EBCA6B;
            return (h ^ (h >>> 15)) & mask;
        }

        public boolean has(int slot, int x, int z) {
            return (used >>> slot & 1L) != 0 && xs[slot] == x && zs[slot] == z;
        }

        public double value(int slot) {
            return values[slot];
        }

        public void put(int slot, int x, int z, double value) {
            xs[slot] = x;
            zs[slot] = z;
            values[slot] = value;
            used |= 1L << slot;
        }

        /** True when this answer has to be recomputed and compared (the router's first answers, then one in 4096 here). */
        public boolean compare() {
            if (VERIFY) return true;
            World w = world;
            if (w.canary > 0) {
                w.canary--;                     // a lost update between threads only compares a few more
                return true;
            }
            return (++served & 4095) == 0;
        }
    }

    /**
     * The table of a FlatCache at a request outside the grid: a real one, Table.NONE (this FlatCache keeps the original
     * code for good), or null while its NoiseChunk is still being constructed (asked again at the next request).
     */
    public static Table table(Object noiseChunk, boolean fromRouter, boolean linked, boolean markerTabled) {
        if (!ACTIVE || !fromRouter || !(noiseChunk instanceof Chunk c)) return Table.NONE;
        if (!c.bons$outsideDecided()) return null;
        World w = c.bons$outsideWorld();
        if (w == null || !w.ok) return Table.NONE;
        if (!linked) {                                      // made while the router was wrapped, but no marker record said so
            if (METRICS) UNLINKED.increment();
            if (!unlinkedSaid) {
                unlinkedSaid = true;
                LOGGER.info("Bons and Furious: vanilla_flat_cache_outside_columns keeps the original code for flat caches whose marker is not known "
                        + "(another mod maps flat_cache markers itself)");
            }
            return Table.NONE;
        }
        if (!markerTabled) {
            if (METRICS) KEPT.increment();
            return Table.NONE;
        }
        if (METRICS) TABLES.increment();
        return new Table(w, c.bons$outsideCells() <= 1 ? 8 : 64);
    }

    /**
     * MarkerOriginMixin, through the FlatCache: is this marker record one of the flat_cache markers that passed the
     * check of the router the FlatCache's NoiseChunk is wrapping? (The NoiseChunk takes its router's verdict before it
     * wraps the router.)
     */
    public static boolean tabled(Object noiseChunk, Object marker) {
        if (!(noiseChunk instanceof Chunk c)) return false;
        World w = c.bons$outsideWorld();
        return w != null && w.tables(marker);
    }

    /** NoiseChunk's constructor ended without its router.mapAll call having gone through the wrapper. */
    public static void noBracket() {
        if (ACTIVE && !noRouterSaid) {
            noRouterSaid = true;
            LOGGER.info("Bons and Furious: vanilla_flat_cache_outside_columns keeps the original code: another mod changes the call "
                    + "that wraps the noise router in NoiseChunk's constructor");
        }
    }

    public static void countServed() {
        if (METRICS) SERVED.increment();
    }

    public static void countComputed() {
        if (METRICS) COMPUTED.increment();
    }

    public static void countUntabled() {
        if (METRICS) UNTABLED.increment();
    }

    /** A compared answer: the original value is returned; a difference switches the router off. */
    public static double compared(Table table, double served, double original, int x, int z) {
        if (METRICS) COMPARED.increment();
        if (Double.doubleToRawLongBits(served) != Double.doubleToRawLongBits(original)) {
            MISMATCHED.increment();
            table.world.differs(original, served, x, z);
        }
        return original;
    }
}
