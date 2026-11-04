package bons.furious.mixin.vanilla_render_tradeoffs;

import bons.furious.patch.vanilla_render_tradeoffs.ItemRenderCap;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_item_entity_render_cap (Minecraft 1.20.1 on Forge 47.4.28, client; since 1.0.49; trade-off, off by default):
 * ItemEntityRenderer.getRenderAmount (m_115042_) returns min(cap, vanilla's 1-5 copies) through ItemRenderCap. Biomancy's
 * and FDLib's injections into render (m_7392_) are a different method. Renderers that override getRenderAmount keep their
 * own count. No Minecraft code is carried.
 */
@Mixin(value = ItemEntityRenderer.class, remap = false)
public abstract class ItemEntityRenderCapMixin {
    @ModifyReturnValue(method = "m_115042_", at = @At("RETURN"))
    private int bons$capCopies(int vanilla) {
        return ItemRenderCap.cap(vanilla);
    }
}
