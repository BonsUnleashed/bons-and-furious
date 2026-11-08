package bons.furious.patch.subtleeffects_c49;

import einstein.subtle_effects.ticking.GeyserManager;
import einstein.subtle_effects.ticking.tickers.TickerManager;
import einstein.subtle_effects.ticking.tickers.geyser.GeyserType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch subtleeffects_display_tick_state_plan (Subtle Effects 1.15.1 for NeoForge 1.21.1, client). Subtle
 * Effects is All Rights Reserved: no Subtle Effects code is carried, only our own answers in front of two of its calls.
 * Ours, idea from Collections Of Optimizations (subtleeffects.displayTickPlan), idea text only; the exact part of that idea.
 *
 * What Subtle Effects does: its ClientLevelMixin.animateTick handler runs inside ClientLevel.doAnimateTick for every
 * ambient sample (1,334 per client tick), right before Block.animateTick. It reads the sampled block state again
 * (getBlockState of an immutable copy of the position vanilla just read) and, for every non-air state, calls
 * GeyserManager.tick, which clones GeyserType.values() and, for each of the three geyser types, reads the block state and
 * the fluid state at the position again (GeyserTicker.isSpawnableBlock) and tests both against that type's configured
 * list of spawnable blocks (a Fzzy Config ValidatedField read with get()); only when one of them is listed does anything
 * else happen.
 *
 * What the switch does:
 * (a) doAnimateTick's own state read is remembered right before Subtle Effects' handler runs (our injection at the same
 *     point, applied first), and the handler's getBlockState of that same position on that level and thread answers it
 *     once. Why identical: both are getBlockState of the same coordinates of the same level a moment apart, with only the
 *     injection callbacks at that point in between (nothing that places blocks); the remembered state is used at most once.
 * (b) GeyserManager.tick is not called when no geyser type's list holds the block at the position or the fluid's block
 *     there: the same level reads (once instead of three times each), the same configured lists, read at the same moment.
 *     Why identical: isSpawnableBlock is true only when the block or the fluid's block is listed (the face check comes
 *     after the list check), and when it is false for every type GeyserManager.tick does nothing else (no random number,
 *     no ticker, no geyser record). Whenever any list holds either block, GeyserManager.tick runs as shipped.
 *
 * Flag: -Dbons_and_furious.subtleeffectsDisplayTickStatePlan=false restores both calls. Shadow mode
 * (-Dbons_and_furious.subtleeffectsDisplayTickStatePlan.shadow=true): nothing is skipped; (a) compares the remembered
 * state with a fresh read, (b) checks that the call it would skip changes no geyser or ticker count (SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class DisplayTickPlan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch (the config switch acts when the classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.subtleeffectsDisplayTickStatePlan", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.subtleeffectsDisplayTickStatePlan.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Plain counters for probes and the offline proof: states handed over, geyser calls left out. */
    public static long reused, geyserSkipped;
    private static volatile boolean announced;

    // the remembered read (the client thread only: doAnimateTick runs there)
    private static Object capLevel;
    private static Thread capThread;
    private static long capPos;
    private static BlockState capState;

    private static GeyserType[] types;

    private DisplayTickPlan() {
    }

    /** doAnimateTick, right before Block.animateTick (and so before Subtle Effects' handler): the state it just read. */
    public static void capture(Object level, BlockPos pos, BlockState state) {
        if (!enabled) {
            capState = null;
            return;
        }
        capLevel = level;
        capThread = Thread.currentThread();
        capPos = pos.asLong();
        capState = state;
    }

    /** The handler's getBlockState(pos): the remembered state when it is the same read, else null (read as shipped). */
    public static BlockState reuse(Object level, BlockPos pos) {
        BlockState s = capState;
        if (s == null || !enabled || level != capLevel || capThread != Thread.currentThread() || pos.asLong() != capPos) return null;
        capState = null;
        reused++;
        if (!announced) announce();
        return s;
    }

    /** True when GeyserManager.tick(level, state, pos) can only do nothing: no geyser type lists the block or fluid block there. */
    public static boolean noGeyser(Level level, BlockPos pos) {
        if (!enabled) return false;
        GeyserType[] t = types;
        if (t == null) types = t = GeyserType.values();
        Block block = level.getBlockState(pos).getBlock();
        Block fluidBlock = level.getFluidState(pos).createLegacyBlock().getBlock();
        for (GeyserType type : t) {
            List<?> listed = (List<?>) type.spawnableBlocks.get();
            if (listed.contains(block) || listed.contains(fluidBlock)) return false;
        }
        geyserSkipped++;
        if (!announced) announce();
        return true;
    }

    private static Field addQueue;

    /**
     * Shadow mode only: the number of geyser records (GeyserManager's active and inactive lists) plus Subtle Effects'
     * queued new tickers, so a geyser call the switch would have left out can be checked for doing nothing.
     */
    public static int geyserRecords() {
        int n = 0;
        for (List<?> l : GeyserManager.ACTIVE_GEYSERS.values()) n += l.size();
        for (List<?> l : GeyserManager.INACTIVE_GEYSERS.values()) n += l.size();
        try {
            if (addQueue == null) {
                Field f = TickerManager.class.getDeclaredField("ADD_QUEUE");
                f.setAccessible(true);
                addQueue = f;
            }
            n += ((List<?>) addQueue.get(null)).size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            n += 0;
        }
        return n;
    }

    /** Shadow mode: a remembered state that differs from a fresh read, or a skipped geyser call that changed something. */
    public static void shadowMismatch(String what) {
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: subtleeffects_display_tick_state_plan shadow mismatch #{}: {}", m, what);
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: subtleeffects_display_tick_state_plan applies (Subtle Effects' display tick reuses the sampled block state and skips geyser checks that cannot match)");
    }
}
