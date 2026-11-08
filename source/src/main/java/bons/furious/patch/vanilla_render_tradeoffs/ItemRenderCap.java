package bons.furious.patch.vanilla_render_tradeoffs;

import bons.pure.config.PureConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_item_entity_render_cap (Minecraft 1.21.1 on NeoForge 21.1.252, client; since 1.0.49;
 * kind change, OFF by default). Idea: Collections Of Optimizations (vanilla.itemEntityRenderCap), idea text only.
 *
 * What the game does. ItemEntityRenderer.render reseeds the renderer's random from the item (getSeedForItemStack) and calls
 * the static renderMultipleFromCount, whose first step is getRenderedAmount(stack count): 1 for one item, 2 for 2-16, 3 for
 * 17-32, 4 for 33-48, 5 for more; each copy renders the whole item model, and only the copies after the first draw random
 * offsets. Vault and ominous item spawner renderers call renderMultipleFromCount too, for the items they show.
 *
 * What the switch does. For the renderMultipleFromCount call of ItemEntityRenderer.render (a dropped stack) on the render
 * thread, getRenderedAmount returns min(cap, vanilla's count), cap = vanilla_item_entity_render_cap.value (1-4, default 1 =
 * COO's default): render arms the cap right before the call and the first getRenderedAmount on the render thread uses it
 * up; every other caller of renderMultipleFromCount keeps vanilla's count. Trade-off: stacks of 2 or more items lying on
 * the ground show fewer copies (with the default, one). Nothing else changes: the renderer reseeds its random from the item
 * before drawing, so the offsets of the copies that are drawn, the bobbing, the spin, the shadow and every other item are as
 * before; no saved data or packet.
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
    /** Set on the render thread right before ItemEntityRenderer.render's renderMultipleFromCount; the next count uses it up. */
    private static boolean armed;
    private static boolean announced;

    private ItemRenderCap() {}

    /** ItemEntityRenderer.render, right before its renderMultipleFromCount call: the next count belongs to a dropped stack. */
    public static void arm() {
        if (enabled && RenderSystem.isOnRenderThread()) armed = true;
    }

    /** ItemEntityRenderer.render, after its renderMultipleFromCount call (also when it threw). */
    public static void disarm() {
        if (RenderSystem.isOnRenderThread()) armed = false;
    }

    /** ItemEntityRenderer.getRenderedAmount's result. */
    public static int cap(int vanilla) {
        if (!armed || !RenderSystem.isOnRenderThread()) return vanilla;
        armed = false;
        if (!enabled || vanilla <= CAP) return vanilla;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_item_entity_render_cap applies (dropped stacks draw at most {} cop{} of their model)", CAP, CAP == 1 ? "y" : "ies");
        }
        return CAP;
    }
}
