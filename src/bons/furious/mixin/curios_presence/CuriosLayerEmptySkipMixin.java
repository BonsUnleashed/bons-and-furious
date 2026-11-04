package bons.furious.mixin.curios_presence;

import bons.furious.patch.curios_presence.CuriosRenderSkip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.client.render.CuriosLayer;

/**
 * curios_render_layer_empty_skip (Curios API 5.14.1+1.20.1, LGPL-3.0; client; since 1.0.49): CuriosLayer.render returns at
 * its HEAD for an entity without a Curios inventory (CuriosRenderSkip.skip), where the shipped method would push the pose,
 * find no inventory and pop it again. Everything else renders as shipped. Our own logic only.
 */
@Mixin(value = CuriosLayer.class, remap = false)
public abstract class CuriosLayerEmptySkipMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void bons$skipNoInventory(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing,
                                      float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (CuriosRenderSkip.skip(entity)) ci.cancel();
    }
}
