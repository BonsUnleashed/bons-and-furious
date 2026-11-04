package bons.furious.mixin.blessfulled_c49;

import bons.furious.patch.blessfulled_c49.HurtOverlaySkip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;

/**
 * blessfulled_geckolib_overlay_null_source (Blessfulled 0.6, All Rights Reserved; client side): a cancellable HEAD inject on
 * BSGeckoLib$FrostboundGeoLayer.render that returns for a mob without a damage source when the setting is "only by player"
 * (Blessfulled threw a NullPointerException there) or the overlay is invisible (HurtOverlaySkip).
 */
@Mixin(targets = "org.aqutheseal.blessfulled.integration.BSGeckoLib$FrostboundGeoLayer", remap = false)
public abstract class FrostboundGeoLayerNullSourceMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$blessfulledC49NullSource(PoseStack pose, GeoAnimatable animatable, BakedGeoModel model, RenderType type,
            MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int light, int overlay, CallbackInfo ci) {
        if (HurtOverlaySkip.nullSourceSkip(animatable)) ci.cancel();
    }
}
