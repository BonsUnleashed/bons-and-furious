package bons.furious.mixin.mowziesmobs_client_c49;

import bons.furious.patch.mowziesmobs_client_c49.LayerBoneScan;
import com.bobmowzie.mowziesmobs.client.render.entity.layer.UmvuthiSunLayer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * mowziesmobs_umvuthana_layer_bone_test (Mowzie's Mobs, custom licence: no code carried; 1.21.1 tested build: Mowzie's Mobs
 * 1.21.1-1.8.2 with GeckoLib 4.9.3; client, since 1.0.49): in UmvuthiSunLayer.renderForBone the pushPose,
 * translateToPivotPoint and popPose calls are left out for bones other than the sun bone it draws on
 * (bons.furious.patch.mowziesmobs_client_c49.LayerBoneScan); one decision per call, shared by the three wrappers.
 *
 * Ported to 1.21.1: GeckoLib's pivot helper is RenderUtil.translateToPivotPoint(PoseStack, GeoBone) (4.9.3; 1.20.1's
 * RenderUtils took a CoreGeoBone). The Umvuthana mask layer and the Umvuthana sun layer no longer have a renderForBone in
 * this build (they draw once per frame on their bone's tracked pose), so only the Umvuthi sun layer is wrapped.
 */
@Mixin(value = UmvuthiSunLayer.class, remap = false)
public abstract class UmvuthiSunLayerScanMixin {
    private static final String METHOD = "renderForBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/bobmowzie/mowziesmobs/server/entity/umvuthana/EntityUmvuthi;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V";

    @WrapOperation(method = METHOD, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"), remap = false)
    private void bons$scanPush(PoseStack pose, Operation<Void> original, @Local(argsOnly = true) GeoBone bone, @Share("bons$boneSkip") LocalBooleanRef skip) {
        boolean s = LayerBoneScan.skip(bone, "sun_render", null);
        skip.set(s);
        if (!s) original.call(pose);
    }

    @WrapOperation(method = METHOD, at = @At(value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/util/RenderUtil;translateToPivotPoint(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/GeoBone;)V"), remap = false)
    private void bons$scanPivot(PoseStack pose, GeoBone bone, Operation<Void> original, @Share("bons$boneSkip") LocalBooleanRef skip) {
        if (!skip.get()) original.call(pose, bone);
    }

    @WrapOperation(method = METHOD, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"), remap = false)
    private void bons$scanPop(PoseStack pose, Operation<Void> original, @Share("bons$boneSkip") LocalBooleanRef skip) {
        if (!skip.get()) original.call(pose);
    }
}
