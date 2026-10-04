package bons.furious.patch.pehkui;

import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch pehkui_scale_tick_callbacks (Pehkui 3.8.2+1.20.1-forge, both sides). No Pehkui code here.
 *
 * Pehkui ticks every scale type of every entity every tick (EntityMixin.pehkui$tick: one ScaleUtils.tickScale call per
 * registered scale type, 32 in 3.8.2). tickScale passed a new capturing lambda to the pre-tick and to the post-tick
 * callback list each time, although both lists are empty unless an addon registers a callback (no 1.20.1 mod in our
 * scan does): about 64 lambda objects per entity per tick. ScaleUtilsTickMixin builds the lambda and calls forEach only
 * for a non-empty list; forEach over an empty ArrayList performs no action. This class holds the runtime switch and
 * announces the switch once, when Pehkui first ticks a scale.
 */
public final class PehkuiScaleTick {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.pehkuiScaleTickCallbacks=false runs Pehkui's original code. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.pehkuiScaleTickCallbacks", "true"));

    static {
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: pehkui_scale_tick_callbacks applies (Pehkui's scale tick builds its "
                + "callback lambdas only for non-empty callback lists){}", enabled ? "" : " - runtime switch off");
    }

    private PehkuiScaleTick() {
    }
}
