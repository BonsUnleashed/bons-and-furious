package bons.furious.patch.pehkui;

import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch pehkui_scale_tick_callbacks (Pehkui; 1.21.1 tested build: Pehkui 3.8.3+1.21-neoforge for
 * NeoForge 1.21.1; both sides). No Pehkui code here.
 *
 * Pehkui ticks every scale type of every entity every tick (EntityMixin.pehkui$tick: one ScaleUtils.tickScale call per
 * registered scale type, 32 in 3.8.3). tickScale passed a new capturing lambda to the pre-tick and to the post-tick
 * callback list each time, although both lists are empty unless an addon registers a callback: about 64 lambda objects
 * per entity per tick. ScaleUtilsTickMixin builds the lambda and calls forEach only for a non-empty list; forEach over an
 * empty ArrayList performs no action. This class holds the runtime switch and announces the switch once, when Pehkui
 * first ticks a scale.
 *
 * Ported to 1.21.1: nothing changed in the target (Pehkui 3.8.3's ScaleUtils.tickScale and ScaleType are the 3.8.2 code);
 * only the tested build in this comment.
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
