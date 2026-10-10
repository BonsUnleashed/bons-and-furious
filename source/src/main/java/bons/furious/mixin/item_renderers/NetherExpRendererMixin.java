package bons.furious.mixin.item_renderers;

import bons.furious.patch.item_renderers.ItemRendererReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * netherexp_item_renderer_reuse (Jaden's Nether Expansion; 1.21.1 tested build: Jadens-Nether-Expansion-2.4.1 for NeoForge
 * 1.21.1), client, render thread: getCustomRenderer() of JNEItemExtensions$1 (JNEItemExtensions.itemExt, the one
 * extensions object JNEClientEvents registers for the Will-o'-Wisp, the Shotgun Fist and the Pump-Charge Shotgun), which
 * builds a new JNEItemRenderer per call (per draw). MixinExtras @WrapMethod: ItemRendererReuse hands out one renderer per
 * extensions object while that is proven identical to a new one; otherwise (other threads, a resource reload in flight,
 * the switch off, another renderer class) the original call runs. See ItemRendererReuse for the per-class proof.
 *
 * Ported to 1.21.1: Nether Expansion 2.4.1 replaced the three per-item anonymous extensions of 2.3.5
 * (JackhammerFistItem$1, PumpChargeShotgunItem$1, ShotgunFistItem$1; 2.4.1 has no Jackhammer Fist item) with this shared one,
 * registered through RegisterClientExtensionsEvent, and moved the renderer to net.jadenxgamer.netherexp.client
 * (still no instance fields of its own; static models only). One memo now serves all three items, as one new renderer per
 * draw served whichever of them was drawn.
 */
@Mixin(targets = "net.jadenxgamer.netherexp.client.rendering.extensions.JNEItemExtensions$1", remap = false)
public abstract class NetherExpRendererMixin implements ItemRendererReuse.SlotHolder {
    @Unique
    private ItemRendererReuse.Slot bons$itemRendererSlot;

    @Override
    public ItemRendererReuse.Slot bons$itemRendererSlot() {
        ItemRendererReuse.Slot s = this.bons$itemRendererSlot;
        if (s == null) this.bons$itemRendererSlot = s = new ItemRendererReuse.Slot();
        return s;
    }

    @WrapMethod(method = "getCustomRenderer")
    private BlockEntityWithoutLevelRenderer bons$reuseRenderer(Operation<BlockEntityWithoutLevelRenderer> original) {
        return ItemRendererReuse.reuse(this, ItemRendererReuse.NETHEREXP, original);
    }
}
