package bons.furious.mixin.item_renderers;

import bons.furious.patch.item_renderers.ItemRendererReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * aquaculture_item_renderer_reuse (Aquaculture; 1.21.1 tested build: Aquaculture 1.21.1-2.7.21 for NeoForge), client,
 * render thread: getCustomRenderer() of BlockItemWithoutLevelRenderer$1 (the extensions of Aquaculture's block items with
 * a 3D item model), which builds a new AquaItemRenderer per call (per draw). MixinExtras @WrapMethod: ItemRendererReuse
 * hands out one renderer per extensions object while that is proven identical to a new one; otherwise (other threads, a
 * resource reload in flight, the switch off, another renderer class) the original call runs. See ItemRendererReuse for
 * the per-class proof.
 *
 * Ported to 1.21.1: no change; the anonymous extensions class (still built per item in initializeClient, which NeoForge
 * 21.1's ClientExtensionsManager still calls) and AquaItemRenderer are the same code.
 */
@Mixin(targets = "com.teammetallurgy.aquaculture.item.BlockItemWithoutLevelRenderer$1", remap = false)
public abstract class AquacultureRendererMixin implements ItemRendererReuse.SlotHolder {
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
        return ItemRendererReuse.reuse(this, ItemRendererReuse.AQUACULTURE, original);
    }
}
