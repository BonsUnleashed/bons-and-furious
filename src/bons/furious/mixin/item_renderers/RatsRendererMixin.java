package bons.furious.mixin.item_renderers;

import bons.furious.patch.item_renderers.ItemRendererReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * rats_item_renderer_reuse (Rats 1.20.1-8.1.3), client, render thread: getCustomRenderer() of RatsBlockItem$1 (Rats' block items with a 3D item model), which builds a new
 * RatsBEWLR per call (per draw). MixinExtras @WrapMethod: ItemRendererReuse hands out one renderer per
 * extensions object while that is proven identical to a new one; otherwise (other threads, a resource reload in flight,
 * the switch off, another renderer class) the original call runs. See ItemRendererReuse for the per-class proof.
 */
@Mixin(targets = "com.github.alexthe666.rats.server.items.RatsBlockItem$1", remap = false)
public abstract class RatsRendererMixin implements ItemRendererReuse.SlotHolder {
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
        return ItemRendererReuse.reuse(this, ItemRendererReuse.RATS, original);
    }
}