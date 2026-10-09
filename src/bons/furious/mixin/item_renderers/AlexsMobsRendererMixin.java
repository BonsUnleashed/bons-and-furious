package bons.furious.mixin.item_renderers;

import bons.furious.patch.item_renderers.ItemRendererReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * alexsmobs_item_renderer_reuse (Alex's Mobs 1.22.9), client, render thread: getCustomRenderer() of AMItemRenderProperties (every Alex's Mobs item with a 3D model), which builds a new
 * AMItemstackRenderer (its entity and failure caches are cleared before each hand-out: AlexsMobsStackRendererMixin) per call (per draw). MixinExtras @WrapMethod: ItemRendererReuse hands out one renderer per
 * extensions object while that is proven identical to a new one; otherwise (other threads, a resource reload in flight,
 * the switch off, another renderer class) the original call runs. See ItemRendererReuse for the per-class proof.
 */
@Mixin(targets = "com.github.alexthe666.alexsmobs.client.render.item.AMItemRenderProperties", remap = false)
public abstract class AlexsMobsRendererMixin implements ItemRendererReuse.SlotHolder {
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
        return ItemRendererReuse.reuse(this, ItemRendererReuse.ALEXSMOBS, original);
    }
}