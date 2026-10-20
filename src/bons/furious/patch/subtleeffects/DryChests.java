package bons.furious.patch.subtleeffects;

import einstein.subtle_effects.ticking.tickers.ChestBlockEntityTicker;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch subtleeffects_dry_chest_guard (Subtle Effects 1.15.1-hotfix.1 for Minecraft 1.20.1, client; our
 * pack does not install Subtle Effects, the switch is for other packs, proven offline on the real classes).
 *
 * <p>Subtle Effects ticks a ChestBlockEntityTicker every client tick for every chest within 48 blocks that a display-tick
 * sample once hit (its "chests open randomly underwater" effect). For a chest that is not underwater the tick ends with
 * nothing done, but first it asks ChestBlock.isChestBlockedAt for the chest and, for a double chest, its other half: a block
 * read above each and an entity query for a sitting cat (an AABB search of the entity sections, a list, Forge's part
 * entities, and Level's "getEntities" profiler counter), then, if the setting asks for it, the soul-sand test, and only then
 * the underwater test.
 *
 * <p>With the switch on, just before the blocked test the tick returns at once when every later branch would end in a plain
 * return anyway: the underwater test answers "return" (the chest, or the other half of a double chest, is not underwater:
 * Subtle Effects' own isUnderwater), both of the ticker's counters (animation ticks, ticks since the last animation) are
 * already 0 (the blocked branch only sets them to 0), and no profiler is active (Minecraft's profiler is
 * InactiveProfiler.INSTANCE, so the entity query's counter increment would do nothing). The skipped work is reads only.
 * A chest underwater, a running animation or a debug profiler (pie chart, profiling): the tick runs as before.
 *
 * <p>-Dbons_and_furious.subtleeffectsDryChestGuard=false turns it off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.subtleeffectsDryChestGuard.shadow=true never skips and counts the ticks the switch would have skipped in
 * SHADOW_CHECKS (SHADOW_MISMATCHES stays 0: the skipped branches have no effect to compare; the rig compares chest
 * animations and sounds ON vs OFF).
 */
public final class DryChests {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.subtleeffectsDryChestGuard", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.subtleeffectsDryChestGuard.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Ticks skipped (statistics; plain, racy). */
    public static long skipped;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announced;

    private DryChests() {
    }

    /**
     * Just before ChestBlockEntityTicker.positionedTick's blocked test: true = return at once. isDouble / other: the tick's
     * own locals (a double chest's other half); the counters: the ticker's fields.
     */
    public static boolean skip(Level level, BlockPos pos, boolean isDouble, BlockPos other, int animationTicks, int ticksSinceLastAnimation) {
        if (!enabled || animationTicks != 0 || ticksSinceLastAnimation != 0) return false;
        if (level.m_46473_() != InactiveProfiler.f_18554_) return false;                 // Level.getProfiler(): a profiler counts the cat query
        boolean dryReturn = !ChestBlockEntityTicker.isUnderwater(level, pos) || (isDouble && !ChestBlockEntityTicker.isUnderwater(level, other));
        if (!dryReturn) return false;
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            return false;
        }
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: subtleeffects_dry_chest_guard: Subtle Effects' chest tickers skip the blocked-chest test for chests that are not underwater");
        }
        return true;
    }
}
