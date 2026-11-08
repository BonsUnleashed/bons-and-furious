package bons.furious.patch.capability_callers;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch goety_capability_handles (Goety 3.2.2 for NeoForge 1.21.1, MIT; both sides; since 1.0.49).
 * Idea: Collections Of Optimizations (goety.fastEmptyAllyCheck), idea text only. No Goety code here.
 *
 * On NeoForge 1.21.1 Goety keeps its soul energy, lichdom and misc data in data attachments: SEHelper.getCapability(Player),
 * LichdomHelper.getCapability(Player) and MiscCapHelper.getCapability(LivingEntity) are each one entity.getData(...) read,
 * with no provider walk and no spare default object, so the Forge build's kept capability values have nothing to save here
 * and are not carried.
 *
 * SEHelper.isAlly(Player, LivingEntity) is getAllyEntities(p).contains(e) || getAllyEntityTypes(p).contains(e.getType()):
 * both helpers build a new list from the soul energy data's ally sets (EntityFinder per UUID). With both sets empty both
 * lists are empty and the answer is false: answered at once (one attachment read, the same value) instead.
 *
 * -Dbons_and_furious.goetyCapabilityHandles=false runs isAlly as shipped every time; .shadow=true (verification runs only)
 * runs the original on every fast answer and counts the answers that are not false.
 */
public final class GoetyHandles {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.goetyCapabilityHandles", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.goetyCapabilityHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announcedAlly;

    private GoetyHandles() {
    }

    /** isAlly shadow compare (fast answer false vs the original's). */
    public static boolean shadowAlly(boolean asked) {
        SHADOW_CHECKS.incrementAndGet();
        if (asked && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: goety_capability_handles shadow mismatch: isAlly answered true with both ally sets empty");
        return asked;
    }

    public static void announceAlly() {
        if (!announcedAlly) {
            announcedAlly = true;
            LOGGER.info("Bons and Furious: goety_capability_handles: SEHelper.isAlly answers players without allies at once");
        }
    }
}
