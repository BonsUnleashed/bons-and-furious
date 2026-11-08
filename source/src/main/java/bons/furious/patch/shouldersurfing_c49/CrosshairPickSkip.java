package bons.furious.patch.shouldersurfing_c49;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.client.world.phys.ObjectPicker;
import com.github.exopandora.shouldersurfing.config.Config;
import com.github.exopandora.shouldersurfing.config.CrosshairConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch shouldersurfing_idle_crosshair_pick_skip (Shoulder Surfing Reloaded 1.21.1-5.3.0, MIT; client
 * side). Ours; no Shoulder Surfing code is carried.
 *
 * What Shoulder Surfing does. CrosshairRenderer.renderTick() runs computeCrosshairOffset() every frame in third person:
 * one pick (a block trace to the interaction range plus an entity scan along it) and, for a static crosshair, a second
 * obstruction pick, then projects the hit to the screen. The resulting offset is read only (a) by
 * computeIsCrosshairVisible, and only to return false when it is null and the crosshair is dynamic, (b) by
 * computeIsObstructionIndicatorVisible, which first returns false when the indicator is disabled, the crosshair is
 * dynamic, or the indicator shows only while aiming and the player is not aiming, and (c) by setupPoseStack /
 * resetPoseStack, which run only when the crosshair is dynamic, the obstruction crosshair shows (never: its condition asks
 * for "not shoulder surfing" while the indicator can only show when shoulder surfing) or the indicator shows. No getter
 * exposes it, and a pick writes no state.
 *
 * What the switch does. When shoulder surfing with a static crosshair and the obstruction indicator cannot show (it is
 * disabled, or it shows only while aiming and the player is not aiming), computeCrosshairOffset returns null without
 * picking: that is Shoulder Surfing's defaults whenever the player is not aiming.
 *
 * Why it is identical. In exactly those states every reader above returns the same answer for a null offset as for any
 * other (a: the crosshair is static; b: false either way; c: never called), and the next frame computes a new offset.
 * It stands aside when the object picker is not Shoulder Surfing's own class (a replaced picker could have side
 * effects).
 *
 * -Dbons_and_furious.shouldersurfingIdleCrosshairPickSkip=false switches it off at run time.
 */
public final class CrosshairPickSkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.shouldersurfingIdleCrosshairPickSkip", "true"));
    private static volatile boolean announced;

    private CrosshairPickSkip() {}

    /** True when computeCrosshairOffset(instance, ..., isCrosshairDynamic, ...) may return null without picking. */
    public static boolean skip(IShoulderSurfing instance, boolean isCrosshairDynamic) {
        if (!enabled || isCrosshairDynamic || !instance.isShoulderSurfing()) return false;
        if (instance.getObjectPicker().getClass() != ObjectPicker.class) return false;
        CrosshairConfig config = Config.CLIENT.getCrosshairConfig();
        if (config.isObstructionIndicatorEnabled() && (instance.isAiming() || !config.isObstructionIndicatorOnlyShownWhenAiming())) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: shouldersurfing_idle_crosshair_pick_skip applies (no crosshair picks while their result cannot be shown)");
        }
        return true;
    }
}
