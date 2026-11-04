package bons.furious.patch.lethality_coo49;

import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lethality_blade_mode_end_phase_only (Lethality 0.4.1, MIT; server; since 1.0.49; kind change, off
 * by default). Idea: Collections Of Optimizations (lethality.skipDuplicateTickPhases), idea text only.
 *
 * BladeModeEvents.onPlayerTick and HFMeowrasamaEvents.onPlayerTick have no phase check, so they run at the START and at the
 * END of every player tick. While Blade Mode is active the first searches every living entity within 5 blocks of the
 * player and gives every mob Slowness III for 20 ticks (an addEffect with two Forge events per mob), and adds its two
 * attribute modifiers when missing; otherwise it removes them (two lookups and two removals that find nothing). The END
 * repeat of the slowness is a refresh that changes nothing (same duration, amplifier and flags), so no packet is sent
 * for it. The second resets the HF Meowrasama's model when Blade Mode is gone.
 *
 * What changes with the switch on: both handlers return at once unless the event's phase is END. Difference: on the tick
 * Blade Mode ends, nearby mobs miss the START refresh (their slowness ends one tick earlier), the player's speed modifiers
 * are added or removed at the END of a tick instead of its START (one tick of movement with the old speed when Blade Mode
 * starts or ends between ticks), and listeners see one MobEffectEvent Applicable / Added pair less per mob per tick.
 * Nothing is saved differently.
 *
 * -Dbons_and_furious.lethalityBladeModeEndPhaseOnly=false runs both handlers as shipped (emergency off).
 */
public final class LethalityBladeMode {
    public static final String KEY = "lethality_blade_mode_end_phase_only";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lethalityBladeModeEndPhaseOnly", "true"));
    private static volatile boolean announced;

    private LethalityBladeMode() {
    }

    /** True when the handler must return at once: the flag is on and this is not the END post of the player tick. */
    public static boolean skip(TickEvent.Phase phase) {
        if (!enabled || phase == TickEvent.Phase.END) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Lethality's Blade Mode and HF Meowrasama player tick handlers run at the END of each player tick only)", KEY);
        }
        return true;
    }
}
