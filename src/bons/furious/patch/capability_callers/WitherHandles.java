package bons.furious.patch.capability_callers;

import com.alexander.witherreincarnated.capabilities.possessed.PossessedCapability;
import com.alexander.witherreincarnated.capabilities.possessed.PossessedCapabilityProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch witherreincarnated_possession_handles (Wither Reincarnated 1.0.5, All Rights Reserved: no Wither
 * Reincarnated code is carried; both sides; since 1.0.49). Idea: Collections Of Optimizations
 * (witherreincarnated.fastPossessionChecks), idea text only.
 *
 * HandlePossessedMobsEvent.POSSESSED (lambda$static$0: getCapability(CAP).isPresent(), then getCapability(CAP).resolve()
 * .get().isPossessed()) is asked from Wither Reincarnated's hooks on Entity.getTeam, isAlliedTo, LivingEntity.canAttack,
 * RandomStrollGoal.canUse, mob sounds and eight goals, so crowds ask it thousands of times per tick; possessedTick asks every
 * Mob every tick. Every getCapability(CAP) there goes to the layer's kept handle (see HandleCallers): the provider answers
 * its private final LazyOptional field (guard lines; CAP is written only by the provider's static initialiser, census),
 * absent entries for entities without the provider.
 *
 * possessedTick also returns at once for a mob whose possession state is idle (idle): for that state the handler's lambda
 * changes nothing (PossessedCapability.tick() writes back the values it read, isPossessed and wasPossessed are false, the
 * resync packet needs worldLoaded) and only reads the level, the block position and Level.isLoaded. The state is read from
 * the capability object resolved exactly as LazyOptional.ifPresent resolves it (getValue, through orElse).
 * -Dbons_and_furious.witherreincarnatedPossessionHandles.idle=false keeps the handler for idle mobs.
 */
public final class WitherHandles {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final HandleCallers KEY = new HandleCallers("witherreincarnated_possession_handles", "witherreincarnatedPossessionHandles", true)
            .register(PossessedCapabilityProvider.CAP);
    public static volatile boolean idleSkip = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.witherreincarnatedPossessionHandles.idle", "true"));
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
}
