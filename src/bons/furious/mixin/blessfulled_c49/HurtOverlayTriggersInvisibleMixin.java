package bons.furious.mixin.blessfulled_c49;

import bons.furious.patch.blessfulled_c49.HurtOverlaySkip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.aqutheseal.blessfulled.triggers.HurtOverlayTriggers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * blessfulled_invisible_overlay_skip (Blessfulled 0.6, All Rights Reserved; client side), ordinary-mob half: a cancellable
 * HEAD inject on HurtOverlayTriggers.renderOverlay that returns while the overlay is invisible (HurtOverlaySkip).
 */
@Mixin(value = HurtOverlayTriggers.class, remap = false)
public abstract class HurtOverlayTriggersInvisibleMixin {
    @Inject(method = "renderOverlay(Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$blessfulledC49InvisibleVanilla(LivingEntityRenderer<?, ?> renderer, LivingEntity entity, PoseStack pose,
            MultiBufferSource buffers, CallbackInfo ci) {
        if (HurtOverlaySkip.invisible(entity)) ci.cancel();
    }
}
