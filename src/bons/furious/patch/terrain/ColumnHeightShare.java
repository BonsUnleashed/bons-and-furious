package bons.furious.patch.terrain;

import bons.pure.terrain.DensityAudit;
import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_noise_column_cache (vanilla 1.20.1 world generation). SRG member names.
 *
 * Structure placement asks NoiseBasedChunkGenerator.getBaseHeight (m_214096_) for ground heights constantly. Each call
 * builds a one-cell NoiseChunk and fills the column's corner densities over the full world height. While generating
 * this pack 21% of the queries repeated a column already answered (same column, same heightmap type), three quarters of
 * them on a different worker thread than the first answer.
 *
 * The query never reads world state: iterateNoiseColumn uses Blender.empty() and the beardifier marker, so its result is
 * a function of the generator's NoiseGeneratorSettings instance, the RandomState, the clamped build height, the column
 * and the heightmap type. Those are the key here. The table is shared by all threads, per RandomState and settings
 * instance: a bounded lock-free direct-mapped array of immutable entries (2^16 slots, about 3 MB when full). A race only
 * costs an extra computation, because an entry is used only when every key field matches.
 *
 * Sharing is exact only while the computation is a pure function, so a world generator keeps the original code when
 *   1. the generator is not exactly NoiseBasedChunkGenerator (a subclass may change what a column query does);
 *   2. its noise router contains a density function type outside {@link DensityAudit}'s known pure types (another mod's
 *      type may carry per-chunk state), or BetterEnd replaces that generator's slice filling;
 * and, as a safety net, the first 256 table hits of each world and then one hit in 4096 are not served but recomputed
 * and compared with the stored value: any difference logs a warning and switches the table off for that world. The
 * recomputed value is the one returned, so a world that fails the check never received a stored value that differed.
 * The one non-vanilla density function a column NoiseChunk carries in this pack (YUNG's Cave Biomes marble-caves wrapper)
 * returns 0 there, because only chunk NoiseChunks are given a biome source.
 *
 * 1.0.36: a per-type miss is also answered from the column's summary when the separate switch vanilla_noise_column_summary
 * recorded one ({@link ColumnSummary}: its own canary, refusal and runtime flag; with that switch off nothing is recorded
 * and this class behaves exactly as before).
 */
public final class ColumnHeightShare {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MISSING = Integer.MIN_VALUE;
    private static final int BITS = 16;
    private static final long CANARY = 256, SAMPLE_MASK = 4095;

    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.columnHeightShare=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.columnHeightShare", "true"));
    static final boolean METRICS = Boolean.getBoolean("bons_and_furious.columnHeightShare.metrics");
    /** Served hits, misses, verified hits and bypassed calls (only with -Dbons_and_furious.columnHeightShare.metrics=true). */
    public static final LongAdder HITS = new LongAdder(), MISSES = new LongAdder(), VERIFIED = new LongAdder(), BYPASSED = new LongAdder();

    private static final MethodHandle SETTINGS;
    static final boolean READY;
    private static final ConcurrentMap<RandomState, World> WORLDS = new MapMaker().weakKeys().concurrencyLevel(16).makeMap();
    private static final ThreadLocal<Pending> PENDING = ThreadLocal.withInitial(Pending::new);
    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 1.0.36: set by the first lookup (this switch's mixin is applied); vanilla_noise_column_summary records only then. */
    static volatile boolean lookupsSeen;

    static {
        MethodHandle h = null;
        boolean ready = false;
        try {
            Field f = NoiseBasedChunkGenerator.class.getDeclaredField("f_64318_");          // settings
            if (f.getType() != Holder.class || Modifier.isStatic(f.getModifiers())) throw new NoSuchFieldException("NoiseBasedChunkGenerator.f_64318_ is not the settings holder");
            f.setAccessible(true);
            h = MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(Holder.class, NoiseBasedChunkGenerator.class));
            ready = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_noise_column_cache is inactive because NoiseBasedChunkGenerator is not the supported 1.20.1 layout ({})", t.toString());
        }
        SETTINGS = h;
        READY = ready;
    }

    private ColumnHeightShare() {
    }

    /** Start of getBaseHeight: the shared height for this query, or {@link #MISSING} to run the original code. */
    public static int lookup(NoiseBasedChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        if (!lookupsSeen) lookupsSeen = true;
        Table t = table(generator, level, random);
        if (t == null) {
            if (METRICS) BYPASSED.increment();
            return MISSING;
        }
        Entry e = t.get(x, z, type.ordinal());
        if (e == null) {
            if (ColumnSummary.recorded) {                      // 1.0.36: vanilla_noise_column_summary (off: never recorded)
                int s = ColumnSummary.answer(t, x, z, type);
                if (s != MISSING) return ColumnSummary.serve(t, x, z, type.ordinal(), s, PENDING.get());
            }
            if (METRICS) MISSES.increment();
            return MISSING;
        }
        long n = t.world.hits.incrementAndGet();
        if (n <= CANARY || (n & SAMPLE_MASK) == 0) {
            PENDING.get().arm(t, x, z, type.ordinal(), e.value());
            if (METRICS) VERIFIED.increment();
            return MISSING;                                    // recompute; store() compares
        }
        if (METRICS) HITS.increment();
        return e.value();
    }

    /** Every return of getBaseHeight: compare an armed verification, store the height, return it unchanged. */
    public static int store(NoiseBasedChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random, int value) {
        Pending p = PENDING.get();
        Table armed = p.table;
        if (armed != null && p.x == x && p.z == z && p.type == type.ordinal()) {
            p.table = null;
            if (p.kind != Pending.ENTRY) ColumnSummary.check(armed, p.kind, x, z, type, p.expected, value);   // 1.0.36
            else if (p.expected != value) {
                armed.world.refuse("a stored height differed from a fresh computation (column " + x + "," + z + " " + type + ": stored "
                        + p.expected + ", computed " + value + ")", true);
                return value;
            }
        }
        Table t = table(generator, level, random);
        if (t != null) t.put(x, z, type.ordinal(), value);
        return value;
    }

    static Table table(NoiseBasedChunkGenerator generator, LevelHeightAccessor level, RandomState random) {
        if (!READY || !enabled || random == null || generator.getClass() != NoiseBasedChunkGenerator.class) return null;
        try {
            World w = WORLDS.get(random);
            if (w == null) w = WORLDS.computeIfAbsent(random, World::new);
            if (w.refusal != null) return null;
            NoiseGeneratorSettings settings = ((Holder<NoiseGeneratorSettings>) SETTINGS.invokeExact(generator)).m_203334_();
            return w.table(settings, level.m_141937_(), level.m_141928_());
        } catch (Throwable t) {
            enabled = false;
            LOGGER.warn("Bons and Furious: vanilla_noise_column_cache switched itself off ({})", t.toString());
            return null;
        }
    }

    /** One RandomState: audit result plus one table per settings instance and build height (normally exactly one). */
    static final class World {
        final AtomicLong hits = new AtomicLong();
        volatile String refusal;
        /** 1.0.36, vanilla_noise_column_summary: its own canary counter and refusal (the per-type table is not affected). */
        final AtomicLong summaryHits = new AtomicLong();
        volatile String summaryRefusal;
        private volatile Table[] tables = new Table[0];

        World(RandomState random) {
            String r;
            try {
                r = DensityAudit.refusal(routerFunctions(random.m_224578_()).toArray());
            } catch (Throwable t) {
                r = "audit failed: " + t;
            }
            if (r != null) refuse(r, false);
        }

        void refuse(String reason, boolean warn) {
            if (refusal != null) return;
            refusal = reason;
            tables = new Table[0];
            if (warn) LOGGER.warn("Bons and Furious: vanilla_noise_column_cache switched off for one world generator: {}", reason);
            else if (LOGGED.add(reason)) LOGGER.info("Bons and Furious: vanilla_noise_column_cache keeps the original code for world generators with {}", reason);
        }

        Table table(NoiseGeneratorSettings settings, int minY, int height) {
            for (Table t : tables) if (t.settings == settings && t.minY == minY && t.height == height) return t;
            synchronized (this) {
                if (refusal != null) return null;
                for (Table t : tables) if (t.settings == settings && t.minY == minY && t.height == height) return t;
                String betterEnd = betterEndTarget(settings);
                if (betterEnd != null) {
                    refuse(betterEnd, false);
                    return null;
                }
                Table[] next = java.util.Arrays.copyOf(tables, tables.length + 1);
                next[tables.length] = new Table(this, settings, minY, height);
                tables = next;
                return next[next.length - 1];
            }
        }
    }

    /** The density functions of a noise router (every component of the record). */
    static List<Object> routerFunctions(NoiseRouter router) throws ReflectiveOperationException {
        List<Object> out = new ArrayList<>(15);
        for (Field f : DensityAudit.fields(NoiseRouter.class)) {
            Object v = f.get(router);
            if (v instanceof DensityFunction) out.add(v);
        }
        return out;
    }

    /** BetterEnd fills the slices of its End NoiseChunks with its own code instead of the density graph. */
    private static String betterEndTarget(NoiseGeneratorSettings settings) {
        for (Class<?> c = settings.getClass(); c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                if (!i.getName().equals("org.betterx.betterend.interfaces.BETargetChecker")) continue;
                try {
                    Method m = i.getMethod("be_isTarget");
                    if (Boolean.TRUE.equals(m.invoke(settings))) return "BetterEnd fills this generator's slices itself";
                } catch (ReflectiveOperationException e) {
                    return "BetterEnd target check failed: " + e;
                }
                return null;
            }
        }
        return null;
    }

    record Entry(int x, int z, int type, int value) {
    }

    static final class Table {
        final World world;
        final NoiseGeneratorSettings settings;
        final int minY, height;
        private final AtomicReferenceArray<Entry> slots = new AtomicReferenceArray<>(1 << BITS);
        /** 1.0.36, vanilla_noise_column_summary: per-column summaries, created on first use. */
        volatile AtomicReferenceArray<ColumnSummary.Summary> summaries;

        Table(World world, NoiseGeneratorSettings settings, int minY, int height) {
            this.world = world;
            this.settings = settings;
            this.minY = minY;
            this.height = height;
        }

        AtomicReferenceArray<ColumnSummary.Summary> summaries() {
            AtomicReferenceArray<ColumnSummary.Summary> s = summaries;
            if (s == null) {
                synchronized (this) {
                    s = summaries;
                    if (s == null) summaries = s = new AtomicReferenceArray<>(1 << ColumnSummary.BITS);
                }
            }
            return s;
        }

        private static int index(int x, int z, int type) {
            long k = ((long) x << 32 ^ (z & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L + type;
            k = (k ^ (k >>> 29)) * 0xBF58476D1CE4E5B9L;
            return (int) ((k ^ (k >>> 32)) >>> (64 - BITS));
        }

        Entry get(int x, int z, int type) {
            Entry e = slots.get(index(x, z, type));
            return e != null && e.x() == x && e.z() == z && e.type() == type ? e : null;
        }

        void put(int x, int z, int type, int value) {
            if (world.refusal != null) return;
            slots.set(index(x, z, type), new Entry(x, z, type, value));
        }
    }

    /** A hit this thread is recomputing instead of serving (only one at a time: getBaseHeight does not nest). */
    static final class Pending {
        /** What is being compared: a per-type entry, a summary answer (canary) or a summary answer in shadow mode (1.0.36). */
        static final int ENTRY = 0, SUMMARY = 1, SUMMARY_SHADOW = 2;
        Table table;
        int x, z, type, expected, kind;

        void arm(Table t, int x, int z, int type, int expected) {
            arm(t, x, z, type, expected, ENTRY);
        }

        void arm(Table t, int x, int z, int type, int expected, int kind) {
            this.table = t;
            this.x = x;
            this.z = z;
            this.type = type;
            this.expected = expected;
            this.kind = kind;
        }
    }
}
