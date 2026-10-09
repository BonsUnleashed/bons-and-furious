package bons.furious.mixin.item_renderers;

import bons.furious.patch.item_renderers.ItemRendererReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * netherexp_item_renderer_reuse (Jaden's Nether Expansion 2.3.5), client, render thread: getCustomRenderer() of the extensions of the Jackhammer Fist, Pump-Charge Shotgun and Shotgun Fist, which builds a new
 * JNEItemRenderer per call (per draw). MixinExtras @WrapMethod: ItemRendererReuse hands out one renderer per
 * extensions object while that is proven identical to a new one; otherwise (other threads, a resource reload in flight,
 * the switch off, another renderer class) the original call runs. See ItemRendererReuse for the per-class proof.
 */
@Mixin(targets = {"net.jadenxgamer.netherexp.registry.item.custom.JackhammerFistItem$1", "net.jadenxgamer.netherexp.registry.item.custom.PumpChargeShotgunItem$1", "net.jadenxgamer.netherexp.registry.item.custom.ShotgunFistItem$1"}, remap = false)
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