package bons.furious.patch.vanilla_render_tradeoffs;

import bons.pure.config.PureConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_item_entity_render_cap (Minecraft 1.20.1 on Forge 47.4.28, client; since 1.0.49; kind
 * change, OFF by default). Idea: Collections Of Optimizations (vanilla.itemEntityRenderCap), idea text only.
 *
 * What the game does. ItemEntityRenderer.getRenderAmount (m_115042_) tells render how many copies of a dropped stack's
 * model to draw: 1 for one item, 2 for 2-16, 3 for 17-32, 4 for 33-48, 5 for more; each copy renders the whole item model.
 *
 * What the switch does. getRenderAmount returns min(cap, vanilla's count), cap = vanilla_item_entity_render_cap.value
 * (1-4, default 1 = COO's default). Trade-off: stacks of 2 or more items lying on the ground show fewer copies (with the
 * default, one). Nothing else changes: the renderer reseeds its random from the item before drawing, so the offsets of the
 * copies that are drawn, the bobbing, the spin, the shadow and every other item are as before; no saved data or packet.
 *
 * -Dbons_and_furious.vanillaItemEntityRenderCap=false switches it off at run time (vanilla's count again).
 */
public final class ItemRenderCap {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaItemEntityRenderCap", "true"));
    /**
     * Maximum copies drawn: vanilla_item_entity_render_cap.value (1-4, default 1). Read once, when the first item entity
     * renders with the switch on (this class is only reached through the switch's mixin).
     */
    public static final int CAP = PureConfig.intValue("vanilla_item_entity_render_cap", 1, 1, 4);
    private static boolean announced;

    private ItemRenderCap() {}

    /** ItemEntityRenderer.getRenderAmount's result. */
    public static int cap(int vanilla) {
        if (!enabled || vanilla <= CAP) return vanilla;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_item_entity_render_cap applies (dropped stacks draw at most {} cop{} of their model)", CAP, CAP == 1 ? "y" : "ies");
        }
        return CAP;
    }
}
