package bons.furious.mixin.item_renderers;

import bons.furious.patch.item_renderers.ItemRendererReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * tacz_item_renderer_reuse (TACZ 1.1.5; guns stay as they are: their renderer keeps an animation state machine), client, render thread: getCustomRenderer() of the extensions of TACZ ammo, attachments and the gun smith table, which builds a new
 * AmmoItemRenderer / AttachmentItemRenderer / GunSmithTableItemRenderer per call (per draw). MixinExtras @WrapMethod: ItemRendererReuse hands out one renderer per
 * extensions object while that is proven identical to a new one; otherwise (other threads, a resource reload in flight,
 * the switch off, another renderer class) the original call runs. See ItemRendererReuse for the per-class proof.
 */
@Mixin(targets = {"com.tacz.guns.item.AmmoItem$1", "com.tacz.guns.item.AttachmentItem$1", "com.tacz.guns.item.GunSmithTableItem$1"}, remap = false)
public abstract class TaczRendererMixin implements ItemRendererReuse.SlotHolder {
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
        return ItemRendererReuse.reuse(this, ItemRendererReuse.TACZ, original);
    }
}