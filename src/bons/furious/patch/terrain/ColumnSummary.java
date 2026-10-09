package bons.furious.patch.terrain;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_noise_column_summary (vanilla 1.20.1 world generation; both sides, wherever terrain is
 * generated). SRG member names. Works on vanilla_noise_column_cache's tables ({@link ColumnHeightShare}) and needs that
 * switch: its lookup at the start of getBaseHeight is where the answers below are served.
 *
 * <p>getBaseHeight(x, z, type) walks the column's noise block states from the top down and returns the Y above the first
 * state its type's stopping predicate (Heightmap.Types.isOpaque(), m_64299_) accepts, or the minimum build height when
 * none does. vanilla_noise_column_cache answers a repeated (column, type) pair from its table; a query of the same column
 * with ANOTHER type still walked the column again, although the earlier walk had already seen the states the new walk
 * would test. Structure ground checks ask OCEAN_FLOOR_WG and WORLD_SURFACE_WG at the same spot, and flatness checks of
 * several mods ask both types on the same grid points.
 *
 * <p>This switch records, per column, the first-match height of every distinct stopping predicate of the heightmap types
 * (identity; four objects in 1.20.1) over the states a walk has seen:
 * <ul>
 * <li>a getBaseHeight walk ({@code iterateNoiseColumn} with one of these predicates) is handed a recording predicate that
 * evaluates every not-yet-matched predicate on each state it is asked about, in order, and then asks the original
 * predicate; the walk stops exactly where it did. The states it saw are the column's states from the top down to its stop;
 * the n-th test is the state n - 1 blocks above the stop, whose height is the walk's own return value minus one (so no
 * height arithmetic of the walk is replicated). A walk whose last test is not its own predicate's first match is not
 * recorded and switches the summary off for that world (another hook would be calling the predicate);</li>
 * <li>a getBaseColumn walk (no predicate, every state stored in the returned NoiseColumn) is summarized from the column
 * array, with the heights iterateNoiseColumn gives those array slots, and marks the column as completely seen.</li>
 * </ul>
 * A later getBaseHeight query that misses vanilla_noise_column_cache's per-type entry is answered from the summary when
 * its predicate's first match lies among the states already seen (the walk for that type tests the same states from the
 * same top in the same order, so its first match is that one), or with the minimum build height when the column was seen
 * completely without a match. The states are a function of the world generator, the build height and the column - the
 * premise vanilla_noise_column_cache already relies on, with its audit (DensityAudit, BetterEnd, exact generator class);
 * the predicates are vanilla's own objects, evaluated here, not inferred. Two walks of a column that disagree, the first
 * 256 summary answers of every world and then one in 4096 (recomputed instead of served and compared at getBaseHeight's
 * return) guard the premise: any difference switches the summary off for that world, never the per-type table.
 *
 * <p>Runtime switch -Dbons_and_furious.columnSummary=false. Shadow mode -Dbons_and_furious.columnSummary.shadow=true: no
 * summary answer is served; each one is compared with the computed height (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20
 * warnings). Storage: per vanilla_noise_column_cache table a lock-free direct-mapped array of immutable summaries (2^16
 * slots, created on first use, about 4 MB when full); a race loses at most one walk's facts.
 */
public final class ColumnSummary {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int UNKNOWN = Integer.MIN_VALUE;
    static final int BITS = 16;
    private static final long CANARY = 256, SAMPLE_MASK = 4095;

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.columnSummary", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.columnSummary.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final boolean METRICS = Boolean.getBoolean("bons_and_furious.columnSummary.metrics");
    /** Walks recorded, answers served, answers recomputed by the canary (only with -Dbons_and_furious.columnSummary.metrics=true). */
    public static final LongAdder RECORDED = new LongAdder(), SERVED = new LongAdder(), VERIFIED = new LongAdder();

    /** The distinct stopping predicates of the heightmap types (by identity) and each type's index among them. */
    private static final Predicate<BlockState>[] PREDICATES;
    private static final int[] TYPE_PREDICATE;
    private static final MethodHandle COLUMN_MIN_Y, COLUMN_STATES;
    static final boolean READY;
    /** Set by the first recorded walk: getBaseHeight lookups consult summaries only from then on. */
    static volatile boolean recorded;
    private static volatile boolean announced;

    static {
        Predicate<BlockState>[] predicates = null;
        int[] typePredicate = null;
        MethodHandle minY = null, states = null;
        boolean ready = false;
        try {
            List<Predicate<BlockState>> distinct = new ArrayList<>();
            Heightmap.Types[] types = Heightmap.Types.values();
            typePredicate = new int[types.length];
            for (Heightmap.Types t : types) {
                Predicate<BlockState> p = t.m_64299_();
                int j = -1;
                for (int i = 0; i < distinct.size(); i++) if (distinct.get(i) == p) j = i;
                if (j < 0) {
                    distinct.add(p);
                    j = distinct.size() - 1;
                }
                typePredicate[t.ordinal()] = j;
            }
            if (distinct.size() > 30) throw new IllegalStateException(distinct.size() + " heightmap predicates");
            @SuppressWarnings("unchecked") Predicate<BlockState>[] arr = distinct.toArray(new Predicate[0]);
            predicates = arr;
            minY = getter("f_151621_", int.class);                                  // NoiseColumn.minY
            states = getter("f_47149_", BlockState[].class);                        // NoiseColumn.column
            ready = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_noise_column_summary is inactive because the heightmap types or NoiseColumn are not the supported 1.20.1 layout ({})", t.toString());
        }
        PREDICATES = predicates;
        TYPE_PREDICATE = typePredicate;
        COLUMN_MIN_Y = minY;
        COLUMN_STATES = states;
        READY = ready;
    }

    private ColumnSummary() {
    }

    private static MethodHandle getter(String name, Class<?> type) throws ReflectiveOperationException {
        Field f = NoiseColumn.class.getDeclaredField(name);
        if (f.getType() != type || Modifier.isStatic(f.getModifiers())) throw new NoSuchFieldException("NoiseColumn." + name + " is not the expected field");
        f.setAccessible(true);
        return MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(type, NoiseColumn.class));
    }

    /** One column's facts: first[j] = height above the first state predicate j accepts, or UNKNOWN; full = every state seen. */
    record Summary(int x, int z, int[] first, boolean full) {
    }

    // ------------------------------------------------------------------------------------------- recording

    /**
     * Start of iterateNoiseColumn (m_224239_): a recorder for this walk, or null when nothing is recorded (switch off, no
     * vanilla_noise_column_cache table for this generator, summary refused for this world, or a stopping predicate that is
     * not one of the heightmap types').
     */
    public static Walk begin(NoiseBasedChunkGenerator generator, LevelHeightAccessor level, RandomState random, int x, int z,
                             MutableObject<NoiseColumn> column, Predicate<BlockState> stop) {
        if (!READY || !enabled || !ColumnHeightShare.lookupsSeen) return null;
        if (stop == null && column == null) return null;
        int inner = -1;
        if (stop != null) {
            for (int j = 0; j < PREDICATES.length; j++) if (PREDICATES[j] == stop) inner = j;
            if (inner < 0) return null;
        }
        ColumnHeightShare.Table t = ColumnHeightShare.table(generator, level, random);
        if (t == null || t.world.summaryRefusal != null) return null;
        return new Walk(t, level, x, z, stop, inner);
    }

    /**
     * The recorder. As a Predicate it is handed to iterateNoiseColumn in place of the original stopping predicate and
     * answers exactly as the original does; getBaseColumn walks (no predicate) are summarized from their column at the end.
     */
    public static final class Walk implements Predicate<BlockState> {
        final ColumnHeightShare.Table table;
        final LevelHeightAccessor level;
        final int x, z, inner;
        final Predicate<BlockState> stop;
        final int[] at;
        int calls, open;

        Walk(ColumnHeightShare.Table table, LevelHeightAccessor level, int x, int z, Predicate<BlockState> stop, int inner) {
            this.table = table;
            this.level = level;
            this.x = x;
            this.z = z;
            this.stop = stop;
            this.inner = inner;
            this.at = new int[PREDICATES.length];
            Arrays.fill(at, -1);
            this.open = (1 << at.length) - 1;
        }

        /** The predicate to hand to the walk: this recorder, or null for a getBaseColumn walk (as the caller passed). */
        public Predicate<BlockState> predicate() {
            return stop == null ? null : this;
        }

        @Override
        public boolean test(BlockState state) {
            int c = calls++;
            int o = open;
            if (o != 0) {
                for (int j = 0; j < at.length; j++) {
                    if ((o & (1 << j)) != 0 && PREDICATES[j].test(state)) {
                        at[j] = c;
                        o &= ~(1 << j);
                    }
                }
                open = o;
            }
            return stop.test(state);
        }

        /** End of iterateNoiseColumn: merge what the walk saw into the column's summary. */
        public void end(OptionalInt result, MutableObject<NoiseColumn> column) {
            try {
                if (stop == null) recordColumn(column == null ? null : column.getValue());
                else recordWalk(result);
            } catch (Throwable t) {
                enabled = false;
                LOGGER.warn("Bons and Furious: vanilla_noise_column_summary switched itself off ({})", t.toString());
            }
        }

        private void recordWalk(OptionalInt result) {
            if (!result.isPresent()) return;          // walked to the bottom without a match: heights not derivable from the result
            int c = calls;
            if (c == 0 || at[inner] != c - 1) {
                refuse(table, "a walk did not stop at its predicate's first match (column " + x + "," + z + ", " + c + " tests)");
                return;
            }
            int top = result.getAsInt() + c - 1;       // height above the first state tested
            int[] first = new int[at.length];
            for (int j = 0; j < first.length; j++) first[j] = at[j] < 0 ? UNKNOWN : top - at[j];
            merge(table, x, z, first, false);
        }

        private void recordColumn(NoiseColumn column) throws Throwable {
            if (column == null) return;
            NoiseSettings ns = table.settings.f_64439_().m_224530_(level);  // noiseSettings().clampToHeightAccessor(level), as the walk
            int cellHeight = ns.m_189212_(), base = Math.floorDiv(ns.f_158688_(), cellHeight), cells = Math.floorDiv(ns.f_64508_(), cellHeight);
            int minY = (int) COLUMN_MIN_Y.invokeExact(column);
            BlockState[] states = (BlockState[]) COLUMN_STATES.invokeExact(column);
            if (cells <= 0 || minY != ns.f_158688_() || states.length != ns.f_64508_() || cells * cellHeight > states.length) return;
            int[] first = new int[PREDICATES.length];
            Arrays.fill(first, UNKNOWN);
            int o = (1 << first.length) - 1;
            for (int i = cells * cellHeight - 1; i >= 0 && o != 0; --i) {
                BlockState s = states[i];
                if (s == null) return;
                for (int j = 0; j < first.length; j++) {
                    if ((o & (1 << j)) != 0 && PREDICATES[j].test(s)) {
                        first[j] = base * cellHeight + i + 1;   // the walk tests array slot i at height (base cell) * cellHeight + i
                        o &= ~(1 << j);
                    }
                }
            }
            merge(table, x, z, first, true);
        }
    }

    static int index(int x, int z) {
        long k = ((long) x << 32 ^ (z & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L + 0x51ED;
        k = (k ^ (k >>> 29)) * 0xBF58476D1CE4E5B9L;
        return (int) ((k ^ (k >>> 32)) >>> (64 - BITS));
    }

    private static void merge(ColumnHeightShare.Table t, int x, int z, int[] first, boolean full) {
        AtomicReferenceArray<Summary> slots = t.summaries();
        int i = index(x, z);
        Summary old = slots.get(i);
        if (old != null && old.x() == x && old.z() == z) {
            for (int j = 0; j < first.length; j++) {
                int a = old.first()[j], b = first[j];
                if (a != UNKNOWN && b != UNKNOWN ? a != b : a != UNKNOWN ? full : b != UNKNOWN && old.full()) {
                    refuse(t, "two walks of column " + x + "," + z + " saw different states (predicate " + j + ": " + a + " vs " + b + ")");
                    return;
                }
                if (b == UNKNOWN) first[j] = a;
            }
            full |= old.full();
        }
        slots.set(i, new Summary(x, z, first, full));
        if (!recorded) recorded = true;
        if (METRICS) RECORDED.increment();
    }

    // ------------------------------------------------------------------------------------------- answers

    /** getBaseHeight's height for this column and type from the summary, or UNKNOWN (= ColumnHeightShare.MISSING). */
    static int answer(ColumnHeightShare.Table t, int x, int z, Heightmap.Types type) {
        if (!enabled || t.world.summaryRefusal != null) return UNKNOWN;
        AtomicReferenceArray<Summary> slots = t.summaries;
        if (slots == null) return UNKNOWN;
        Summary s = slots.get(index(x, z));
        if (s == null || s.x() != x || s.z() != z) return UNKNOWN;
        int v = s.first()[TYPE_PREDICATE[type.ordinal()]];
        if (v != UNKNOWN) return v;
        return s.full() ? t.minY : UNKNOWN;                 // seen completely without a match: getBaseHeight's orElse
    }

    /** A summary answer: served, or (canary, shadow mode) armed for comparison at getBaseHeight's return. */
    static int serve(ColumnHeightShare.Table t, int x, int z, int type, int value, ColumnHeightShare.Pending pending) {
        if (SHADOW) {
            pending.arm(t, x, z, type, value, ColumnHeightShare.Pending.SUMMARY_SHADOW);
            return ColumnHeightShare.MISSING;
        }
        long n = t.world.summaryHits.incrementAndGet();
        if (n <= CANARY || (n & SAMPLE_MASK) == 0) {
            pending.arm(t, x, z, type, value, ColumnHeightShare.Pending.SUMMARY);
            if (METRICS) VERIFIED.increment();
            return ColumnHeightShare.MISSING;                  // recompute; check() compares
        }
        if (METRICS) SERVED.increment();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_noise_column_summary answers ground-height queries from earlier walks of the same column");
        }
        return value;
    }

    /** getBaseHeight's return after an armed summary answer: compare it with the computed height. */
    static void check(ColumnHeightShare.Table t, int kind, int x, int z, Heightmap.Types type, int expected, int value) {
        if (kind == ColumnHeightShare.Pending.SUMMARY_SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (expected == value) return;
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: vanilla_noise_column_summary shadow mismatch {} at column {},{} {}: summary {}, computed {}", n, x, z, type, expected, value);
            return;
        }
        if (expected != value) refuse(t, "a summary answer differed from a fresh computation (column " + x + "," + z + " " + type + ": summary " + expected + ", computed " + value + ")");
    }

    private static void refuse(ColumnHeightShare.Table t, String reason) {
        ColumnHeightShare.World w = t.world;
        if (w.summaryRefusal != null) return;
        w.summaryRefusal = reason;
        LOGGER.warn("Bons and Furious: vanilla_noise_column_summary switched off for one world generator: {}", reason);
    }
}
