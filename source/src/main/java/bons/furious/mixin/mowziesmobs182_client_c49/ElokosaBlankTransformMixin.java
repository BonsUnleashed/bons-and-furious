package bons.furious.mixin.mowziesmobs182_client_c49;

import bons.furious.patch.mowziesmobs182_client_c49.ElokosaTransform;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * mowziesmobs_elokosa_blank_transform_skip (Mowzie's Mobs, custom licence: no code carried; 1.21.1 tested build: Mowzie's
 * Mobs 1.21.1-1.8.2 with GeckoLib 4.9.3; client; since 1.0.49; an exact variant): the re-render of the Elokosa's transform
 * layer keeps its vertices out of the translucent buffer while their alpha is 0 and no shader pack is in use
 * (ElokosaTransform). Our own logic only.
 *
 * Ported to 1.21.1: GeckoLib 4.9.3's GeoRenderer.reRender takes the colour as one ARGB int (the layer passes
 * FastColor.ARGB32.colorFromFloat(opacity, 1, 1, 1)) instead of four floats, and GeoAnimatable moved to
 * software.bernie.geckolib.animatable.
 */
@Pseudo
@Mixin(targets = "com.bobmowzie.mowziesmobs.client.render.entity.layer.ElokosaTransformGeoLayer", remap = false)
public abstract class ElokosaBlankTransformMixin {
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/bobmowzie/mowziesmobs/server/entity/elokosa/EntityElokosa;"
            + "Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V",
            at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib/renderer/GeoRenderer;reRender(Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lsoftware/bernie/geckolib/animatable/GeoAnimatable;"
                    + "Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;FIII)V", remap = false),
            require = 1, allow = 1, remap = false)
    private void bons$m182BlankTransform(GeoRenderer<?> renderer, BakedGeoModel model, PoseStack poseStack, MultiBufferSource buffers, GeoAnimatable animatable,
                                         RenderType renderType, VertexConsumer buffer, float partialTick, int light, int overlay, int colour,
                                         Operation<Void> original) {
        ElokosaTransform.reRender(renderer, model, poseStack, buffers, animatable, renderType, buffer, partialTick, light, overlay, colour, original);
    }
}
