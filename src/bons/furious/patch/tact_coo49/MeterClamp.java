package bons.furious.patch.tact_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch tact_subterranodon_meter_clamp (TACT 1.3.0 / 1.5.3 with Alex's Caves 2.0.2, both sides).
 *
 * Alex's Caves keeps a subterranodon's flight meter in 0..1: every caller of SubterranodonEntity.setMeterAmount clamps
 * the new value first. TACT's flight meter speed options rewrite that value at the start of setMeterAmount
 * (SubterranodonEntityMixin.tact_subterranodonFlight): the step from the current meter to the asked value is multiplied
 * by the recharge or the usage multiplier. Because the step is scaled after Alex's Caves clamped it, a multiplier above 1
 * stores a meter below 0 (a drain past empty, which then has to be climbed back out of) or above 1 (a recharge past full),
 * and the bar is drawn from that out-of-range number. With both multipliers at 1 (TACT's defaults) nothing changes.
 *
 * Gameplay fix (off by default): the value TACT's handler returns is held to the range Alex's Caves asked within:
 * [min(0, asked), max(1, asked)]. A value Alex's Caves itself asks for is never changed (it lies in that range by
 * construction), only TACT's overshoot is cut. Runtime flag -Dbons_and_furious.tactSubterranodonMeterClamp=false.
 * TACT is LGPL-3.0, Alex's Caves All Rights Reserved: no code of either is carried.
 */
public final class MeterClamp {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tactSubterranodonMeterClamp", "true"));
    private static volatile boolean announced;

    private MeterClamp() {
    }

    /** TACT's result held to [min(0, asked), max(1, asked)]; NaN stays NaN, as before. */
    public static float clamp(float stored, float asked) {
        if (!enabled) return stored;
        float lo = Math.min(0f, asked), hi = Math.max(1f, asked);
        if (stored < lo) {
            announce();
            return lo;
        }
        if (stored > hi) {
            announce();
            return hi;
        }
        return stored;
    }

    private static void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: tact_subterranodon_meter_clamp: TACT's subterranodon flight meter speeds no longer push the meter outside 0 to 1");
    }
}
