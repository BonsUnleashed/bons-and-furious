package bons.furious.patch.capability_callers;

import com.alexander.witherreincarnated.capabilities.possessed.PossessedCapability;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch witherreincarnated_possession_handles (Wither Reincarnated 1.0.5 for NeoForge 1.21.1, All Rights
 * Reserved: no Wither Reincarnated code is carried; both sides; since 1.0.49). Idea: Collections Of Optimizations
 * (witherreincarnated.fastPossessionChecks), idea text only.
 *
 * On NeoForge 1.21.1 the possession state is a NeoForge data attachment (WRAttachments.POSSESSED): the POSSESSED predicate is
 * hasData + getData, one identity map read each, with no provider walk, so the Forge build's kept handles have nothing to
 * save here and are not carried.
 *
 * HandlePossessedMobsEvent.possessedTick (EntityTickEvent.Pre, every entity, both sides) still runs for every Mob on every
 * tick. It returns at once for a mob whose possession state is idle (idle): for that state the handler changes nothing
 * (PossessedCapability.tick() writes back the values it read, isPossessed and wasPossessed are false, the resync packet
 * needs worldLoaded) and only reads the level, the block position and Level.isLoaded. A mob without the attachment yet runs
 * the handler as shipped, whose getData creates it, so which mobs carry and save the attachment does not change.
 *
 * -Dbons_and_furious.witherreincarnatedPossessionHandles=false (or .idle=false) runs the handler for idle mobs.
 * -Dbons_and_furious.witherreincarnatedPossessionHandles.shadow=true (verification runs only) runs the handler on every idle
 * skip and counts the states it changed.
 */
public final class WitherHandles {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.witherreincarnatedPossessionHandles", "true"))
            && !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.witherreincarnatedPossessionHandles.idle", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.witherreincarnatedPossessionHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private WitherHandles() {
    }

    /** True when possessedTick's handler changes nothing for this possession state. */
    public static boolean idle(PossessedCapability c) {
        boolean idle = c.possessionTime <= 0 && !c.wasPossessed && c.possessionCooldown == 0 && c.possessedFor == 0
                && c.possessedBy == null && c.possessedByUUID == null && !c.worldLoaded;
        if (idle && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: witherreincarnated_possession_handles: mobs with an idle possession state skip Wither Reincarnated's possession tick");
        }
        return idle;
    }

    /** Shadow mode: the state after the handler ran on a state idle() accepted. */
    public static void shadow(PossessedCapability c) {
        SHADOW_CHECKS.incrementAndGet();
        if (!idle(c) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: witherreincarnated_possession_handles shadow mismatch: the possession tick changed an idle state ({} ticks possessed, cooldown {}, for {})",
                    c.possessionTime, c.possessionCooldown, c.possessedFor);
        }
    }
}
