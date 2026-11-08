package bons.furious.patch.structurify_height_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch structurify_height_cache_primitive (Structurify 2.0.42 for NeoForge 1.21.1, server side incl. the
 * integrated server). Ours; no Structurify code is carried (CC BY-NC-ND 4.0): the mixin only redirects Structurify's four
 * calls into its own height cache (get/put of the first free and first occupied height) to this table.
 *
 * What Structurify does. Its @WrapMethod handlers on ChunkGenerator.getFirstFreeHeight and getFirstOccupiedHeight look
 * every height up in a per-thread LinkedHashMap (access order, 16,384 entries): every lookup builds a key object
 * (generator, x, z, heightmap type, LevelHeightAccessor, RandomState; three identity hashes), our structurify_height_cache
 * key fix adds a WeakReference per key, the map walk compares the key and relinks the entry, and the value is a boxed
 * Integer. Both handlers share the entry: it stores the first free height F and the occupied handler answers F - 1 (and
 * stores occupied + 1).
 *
 * What the switch does. The same mapping (generator, x, z, type, accessor, random state) -> F lives in a per-thread,
 * 4-way set-associative table of primitive slots (4,096 sets, the same 16,384 entries as Structurify's map; least recently
 * used way replaced, as Structurify's LRU does inside one set): x/z packed in a long, the type ordinal, the generator and
 * random state compared by identity, the accessor held WEAKLY and compared by identity (a collected accessor matches
 * nothing, so a cached height pins no finished chunk or region, as with structurify_height_cache), and F kept as the same
 * boxed Integer Structurify would return (F - 1 boxed once, lazily). A hit allocates nothing; a miss answers null exactly
 * like Structurify's empty cache, Structurify's handler computes the height with the ORIGINAL method and calls its put,
 * which lands here. Structurify's own map then stays empty.
 *
 * Why the result is identical. Both caches answer either a stored F that came from the original method for the same
 * arguments, or null (then the original runs). The original is ChunkGenerator's own body (getBaseHeight, resp.
 * getBaseHeight - 1; fingerprinted), and getBaseHeight is a pure function of its arguments for a given generator, so
 * whether an entry is present - the only thing an eviction policy changes - never changes an answer. Structurify's key
 * holds the same six components with the same identity rules, so a stored F is only ever reused for exactly the arguments
 * Structurify would reuse it for. A null accessor is never cached here (Structurify's cache runs, as shipped).
 *
 * -Dbons_and_furious.structurifyHeightCachePrimitive=false switches it off at run time (Structurify's cache path).
 * -Dbons_and_furious.structurifyHeightCachePrimitive.shadow=true (verification runs): the puts also go to Structurify's
 * cache, and every hit here is compared with Structurify's own lookup when it holds the key (SHADOW_CHECKS /
 * SHADOW_MISMATCHES; WARN for the first 20 mismatches).
 */
public final class HeightTable {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.structurifyHeightCachePrimitive", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.structurifyHeightCachePrimitive.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** For the harness: hits and stores (all threads; incremented only in shadow mode or when COUNT is set). */
    public static final boolean COUNT = Boolean.getBoolean("bons_and_furious.structurifyHeightCachePrimitive.count");
    public static final AtomicLong HITS = new AtomicLong(), STORES = new AtomicLong();
    private static volatile boolean announced;

    static final int WAYS = 4;
    static final int SETS = 4096;                 // 16,384 entries, Structurify's MAX_CACHE_SIZE
    static final int SLOTS = WAYS * SETS;

    private HeightTable() {
    }

    /** One thread's table. */
    static final class Table {
        final long[] xz = new long[SLOTS];
        final byte[] type = new byte[SLOTS];      // ordinal + 1; 0 = empty slot
        final Object[] generator = new Object[SLOTS];
        final Object[] random = new Object[SLOTS];
        @SuppressWarnings("unchecked")
        final WeakReference<LevelHeightAccessor>[] accessor = new WeakReference[SLOTS];
        final Integer[] free = new Integer[SLOTS];
        final Integer[] occupied = new Integer[SLOTS];
        final int[] used = new int[SLOTS];
        int clock;
        /** The weak reference of the last stored accessor: consecutive stores for one accessor share it. */
        WeakReference<LevelHeightAccessor> lastRef;
    }

    private static final ThreadLocal<Table> TABLES = ThreadLocal.withInitial(Table::new);

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int set(int x, int z, int t) {
        int h = x * 0x9E3779B1 + z * 0x85EBCA77 + t * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 13;
        return (h & (SETS - 1)) * WAYS;
    }

    /** The slot holding exactly these arguments, or -1. */
    static int find(Table tb, ChunkGenerator generator, long key, int t, LevelHeightAccessor accessor, RandomState random, int base) {
        for (int i = base, end = base + WAYS; i < end; i++) {
            if (tb.xz[i] == key && tb.type[i] == t && tb.generator[i] == generator && tb.random[i] == random) {
                WeakReference<LevelHeightAccessor> ref = tb.accessor[i];
                if (ref != null && ref.get() == accessor) return i;
            }
        }
        return -1;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: structurify_height_cache_primitive applies (Structurify's terrain height cache uses a primitive per-thread table){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }

    /** In place of Structurify's ChunkGeneratorHeightCache.getFirstFreeHeight: the stored first free height, or null. */
    public static Integer lookupFree(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor,
                                     RandomState random, Operation<Integer> original) {
        if (!enabled || accessor == null || type == null) return original.call(generator, x, z, type, accessor, random);
        Table tb = TABLES.get();
        int t = type.ordinal() + 1;
        int base = set(x, z, t);
        int i = find(tb, generator, pack(x, z), t, accessor, random, base);
        if (i < 0) return null;
        tb.used[i] = ++tb.clock;
        Integer f = tb.free[i];
        if (COUNT) HITS.incrementAndGet();
        if (SHADOW) shadow(original.call(generator, x, z, type, accessor, random), f, "free", x, z);
        return f;
    }

    /** In place of Structurify's ChunkGeneratorHeightCache.getFirstOccupiedHeight: the stored first free height - 1, or null. */
    public static Integer lookupOccupied(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor,
                                         RandomState random, Operation<Integer> original) {
        if (!enabled || accessor == null || type == null) return original.call(generator, x, z, type, accessor, random);
        Table tb = TABLES.get();
        int t = type.ordinal() + 1;
        int base = set(x, z, t);
        int i = find(tb, generator, pack(x, z), t, accessor, random, base);
        if (i < 0) return null;
        tb.used[i] = ++tb.clock;
        Integer o = tb.occupied[i];
        if (o == null) tb.occupied[i] = o = Integer.valueOf(tb.free[i] - 1);    // Structurify: baseHeight - 1, boxed
        if (COUNT) HITS.incrementAndGet();
        if (SHADOW) shadow(original.call(generator, x, z, type, accessor, random), o, "occupied", x, z);
        return o;
    }

    /** In place of Structurify's putFirstFreeHeight: F = height. */
    public static void storeFree(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor,
                                 RandomState random, int height, Operation<Void> original) {
        if (!enabled || accessor == null || type == null) {
            original.call(generator, x, z, type, accessor, random, height);
            return;
        }
        store(generator, x, z, type, accessor, random, height);
        if (SHADOW) original.call(generator, x, z, type, accessor, random, height);
    }

    /** In place of Structurify's putFirstOccupiedHeight: F = height + 1 (what Structurify stores). */
    public static void storeOccupied(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor,
                                     RandomState random, int height, Operation<Void> original) {
        if (!enabled || accessor == null || type == null) {
            original.call(generator, x, z, type, accessor, random, height);
            return;
        }
        store(generator, x, z, type, accessor, random, height + 1);
        if (SHADOW) original.call(generator, x, z, type, accessor, random, height);
    }

    static void store(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor, RandomState random, int f) {
        announce();
        Table tb = TABLES.get();
        int t = type.ordinal() + 1;
        long key = pack(x, z);
        int base = set(x, z, t);
        int i = find(tb, generator, key, t, accessor, random, base);
        if (i < 0) {
            i = base;                                  // an empty or dead way first, else the least recently used one
            int oldest = -1;
            for (int w = base, end = base + WAYS; w < end; w++) {
                WeakReference<LevelHeightAccessor> held = tb.accessor[w];
                if (tb.type[w] == 0 || held == null || held.get() == null) { i = w; break; }
                int age = tb.clock - tb.used[w];       // wrap-safe distance from the per-thread clock
                if (age > oldest) { oldest = age; i = w; }
            }
        }
        WeakReference<LevelHeightAccessor> ref = tb.lastRef;
        if (ref == null || ref.get() != accessor) tb.lastRef = ref = new WeakReference<>(accessor);
        tb.xz[i] = key;
        tb.type[i] = (byte) t;
        tb.generator[i] = generator;
        tb.random[i] = random;
        tb.accessor[i] = ref;
        tb.free[i] = Integer.valueOf(f);
        tb.occupied[i] = null;
        tb.used[i] = ++tb.clock;
        if (COUNT) STORES.incrementAndGet();
    }

    private static void shadow(Integer structurify, Integer ours, String what, int x, int z) {
        if (structurify == null) return;               // Structurify's map does not hold the key (it may have evicted it)
        SHADOW_CHECKS.incrementAndGet();
        if (!structurify.equals(ours)) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: structurify_height_cache_primitive shadow mismatch #{} ({} height at {}, {}): Structurify {} vs table {}",
                    m, what, x, z, structurify, ours);
        }
    }

    /** For the harness: empty this thread's table. */
    public static void clearThisThread() {
        TABLES.remove();
    }
}
