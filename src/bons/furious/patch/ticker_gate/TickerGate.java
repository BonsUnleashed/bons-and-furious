package bons.furious.patch.ticker_gate;

import bons.furious.mixin.ticker_gate.DistanceManagerTrackerAccessor;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_ticker_range_memo (Minecraft 1.20.1 on Forge 47.4.16; acts on the server thread,
 * including the integrated server). No Minecraft code here. Our own design, found in our own 1.0.26 recording:
 * the cost left over by vanilla_block_ticking_range_memo.
 *
 * Every tick, Level.tickBlockEntities asks shouldTickBlocksAt(pos) for every block-entity ticker in the level's list.
 * ServerLevel answers with DistanceManager.inBlockTickingRange, a lookup of the chunk in TickingTracker's ticking-level
 * hash map. vanilla_block_ticking_range_memo keeps one (chunk, level, version) per tracker, which helps only while
 * consecutive tickers sit in the same chunk; most loaded chunks hold one or two tickers, and sculk sensors and other
 * tickers ask about other chunks in between. The rest, the map's key and value arrays at a hashed slot, usually from
 * outside the cache, was 3.6-3.7% of the integrated server thread (cli10_jfr1, steady and flight windows).
 *
 * Each ticker wrapper (LevelChunk$RebindableTickingBlockEntityWrapper) now remembers its own last answer together with
 * when it was known to be current: the tracker keeps a write version and a long epoch for each of 256 groups of 8x8-chunk
 * regions, and bumps the version and the group of a chunk before and after every TickingTracker.setLevel (the only code
 * that writes the map). The loop hands its ticker to the range check through the level (LevelTickerContextMixin); the
 * check answers from the wrapper while the chunk is the remembered one and either the tracker's version is unchanged (no
 * write at all, read from the tracker object the original lookup reads too) or the chunk's region epoch is unchanged (no
 * write in its region; the version is then renewed), and otherwise asks inBlockTickingRange as before and remembers that
 * answer. Any write changes the version, and a write to a chunk changes its group's epoch, so an answer can never be
 * stale; version and epochs only grow (64 bits).
 *
 * Asks as before for: the switch off, a ticker of another class, a call on another thread than the loop's, a distance
 * manager whose class overrides inBlockTickingRange, a tracker of another class than TickingTracker.
 *
 * -Dbons_and_furious.tickerRangeMemo=false asks every time. -Dbons_and_furious.tickerRangeMemo.shadow=true (verification
 * runs) also asks for every remembered answer and compares (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged;
 * SHADOW_FILLS counts the answers asked and remembered).
 */
public final class TickerGate {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tickerRangeMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.tickerRangeMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_FILLS = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced, warned;
    /** Number of epoch groups (power of two). */
    public static final int GROUPS = 256;

    private TickerGate() {
    }

    /** A fresh epoch table: every group starts at 1, so an unset memo (stamp 0, no table) never matches. */
    public static long[] newEpochs() {
        long[] e = new long[GROUPS];
        Arrays.fill(e, 1L);
        return e;
    }

    /** The epoch group of a chunk (ChunkPos.toLong layout: x in the low 32 bits, z in the high): its 8x8-chunk region, hashed. */
    public static int group(long chunk) {
        int h = (((int) chunk) >> 3) * 0x9E3779B1 + (((int) (chunk >>> 32)) >> 3) * 0x7FEB352D;
        return (h ^ (h >>> 16)) & (GROUPS - 1);
    }

    /** TickingTracker.setLevel(chunk, level) is about to write, or has just written, the map entry of chunk. */
    public static void bump(long[] epochs, long chunk) {
        epochs[group(chunk)]++;
    }

    private static final ClassValue<Boolean> PLAIN_MANAGER = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("m_183916_", long.class).getDeclaringClass() == DistanceManager.class;
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
        }
    };

    /** ServerLevel.shouldTickBlocksAt(long)'s call of distanceManager.inBlockTickingRange(chunk); level is that ServerLevel. */
    public static boolean inRange(Object level, DistanceManager manager, long chunk) {
        TickerContext context = (TickerContext) level;
        Object ticker = context.bons$tickerContext();
        if (ticker == null || context.bons$tickerThread() != Thread.currentThread()) return manager.m_183916_(chunk);
        context.bons$clearTickerContext();
        if (!enabled || !(ticker instanceof TickerRangeMemo memo)) return manager.m_183916_(chunk);
        TickingTracker tracker = ((DistanceManagerTrackerAccessor) manager).bons$tickingTracker();
        if (tracker == null) return manager.m_183916_(chunk);
        TrackerEpochs writes = (TrackerEpochs) tracker;
        long[] epochs = writes.bons$tickerEpochs();
        // a remembered answer is only ever taken for a plain manager and tracker (below), and the epoch table is the
        // tracker's own, so matching table and chunk plus an unchanged version (no write at all since the answer was
        // current) or an unchanged region epoch (no write in the chunk's region) is enough here
        if (memo.bons$rangeEpochs() == epochs && memo.bons$rangeChunk() == chunk) {
            long version = writes.bons$tickerVersion();
            if (memo.bons$rangeVersion() == version) {
                boolean remembered = memo.bons$rangeAnswer();
                if (SHADOW) shadow(manager, chunk, remembered);
                return remembered;
            }
            if (memo.bons$rangeStamp() == epochs[group(chunk)]) {
                memo.bons$rangeRenew(version);
                boolean remembered = memo.bons$rangeAnswer();
                if (SHADOW) shadow(manager, chunk, remembered);
                return remembered;
            }
        }
        if (tracker.getClass() != TickingTracker.class || !PLAIN_MANAGER.get(manager.getClass())) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Bons and Furious: vanilla_ticker_range_memo asks as before for {} / {} (inBlockTickingRange or the ticking tracker is replaced)",
                        manager.getClass().getName(), tracker.getClass().getName());
            }
            return manager.m_183916_(chunk);
        }
        long version = writes.bons$tickerVersion(), stamp = epochs[group(chunk)];
        boolean answer = manager.m_183916_(chunk);
        memo.bons$rangeRemember(epochs, chunk, version, stamp, answer);
        if (SHADOW) SHADOW_FILLS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_ticker_range_memo applies (block-entity tickers remember their ticking-range answer until their region changes){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return answer;
    }

    private static void shadow(DistanceManager manager, long chunk, boolean remembered) {
        boolean asked = manager.m_183916_(chunk);
        SHADOW_CHECKS.incrementAndGet();
        if (asked != remembered) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_ticker_range_memo shadow mismatch #{}: chunk {} remembered {} but inBlockTickingRange says {}",
                    m, chunk, remembered, asked);
        }
    }
}
