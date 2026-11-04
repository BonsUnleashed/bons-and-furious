package bons.furious.patch.iceandfire_ai_c49;

import bons.pure.config.PureConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch iceandfire_dragon_target_search_height (Ice and Fire 2.1.13-1.20.1-beta-5, server, since 1.0.49;
 * a trade-off, off by default). Idea: Collections Of Optimizations (iceandfire.dragonTargetSearchHeight), idea text only.
 * Our own logic; no Ice and Fire code (LGPL-3.0 target, wrapper only).
 *
 * DragonAITarget and DragonAITargetNonTamed (the dragons' nearest-attackable-target goals) search a box around the dragon
 * inflated by the follow range in all three directions (64 blocks without the attribute, up to 128 for wild dragons), so
 * every target search lists the entities of a cube up to 256 blocks tall.
 *
 * What changes: the box's vertical half-extent is the switch's value (default 32 blocks) instead of the follow range; the
 * horizontal extent is unchanged. Dragons no longer pick targets farther above or below them than that. With the runtime
 * flag -Dbons_and_furious.iceandfireDragonTargetSearchHeight=false the stock box is used.
 */
public final class DragonSearchHeight {
    public static final String KEY = "iceandfire_dragon_target_search_height";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfireDragonTargetSearchHeight", "true"));
    /** Vertical half-extent of the search box in blocks (read once, on the switch-on path: this class loads from the mixin). */
    public static final int HEIGHT = PureConfig.intValue("iceandfire_dragon_target_search_height", 32, 1, 512);
    private static volatile boolean announced;

    private DragonSearchHeight() {
    }

    /** The vertical inflate amount for a box stock would inflate by `stock` (the follow range). */
    public static double height(double stock) {
        if (!enabled) return stock;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (dragons search for targets up to {} blocks above and below them)", KEY, HEIGHT);
        }
        return HEIGHT;
    }
}
