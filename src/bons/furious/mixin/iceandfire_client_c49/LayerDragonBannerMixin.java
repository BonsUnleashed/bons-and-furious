package bons.furious.mixin.iceandfire_client_c49;

import bons.furious.patch.iceandfire_client_c49.DragonLayers;
import com.github.alexthe666.iceandfire.client.render.entity.layer.LayerDragonBanner;
import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;

/**
 * iceandfire_dragon_layers_empty_skip (Ice and Fire 2.1.13-1.20.1-beta-5, client, since 1.0.49): LayerDragonBanner.render
 * returns at once when the dragon holds no banner in its off hand (bons.furious.patch.iceandfire_client_c49.DragonLayers
 * .noBanner); every other call, and every call with the runtime flag off, runs the original.
 */
@Mixin(value = LayerDragonBanner.class, remap = false)
public abstract class LayerDragonBannerMixin {
    @WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/github/alexthe666/iceandfire/entity/EntityDragonBase;FFFFFF)V", remap = false)
    private void bons$skipNoBanner(PoseStack pose, MultiBufferSource buffers, int light, EntityDragonBase dragon, float limbSwing, float limbSwingAmount,
                                   float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, Operation<Void> original) {
        if (DragonLayers.noBanner(dragon)) return;
        original.call(pose, buffers, light, dragon, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
    }
}
