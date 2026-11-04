package bons.furious.mixin.mowziesmobs_client_c49;

import bons.furious.patch.mowziesmobs_client_c49.LayerBoneScan;
import com.bobmowzie.mowziesmobs.client.render.entity.layer.UmvuthanaSunLayer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;

/**
 * mowziesmobs_umvuthana_layer_bone_test (Mowzie's Mobs 1.7.3, client, since 1.0.49): in UmvuthanaSunLayer.renderForBone
 * the pushPose, translateToPivotPoint and popPose calls are left out for bones other than the head bone it draws on
 * (bons.furious.patch.mowziesmobs_client_c49.LayerBoneScan); one decision per call, shared by the three wrappers.
 */
@Mixin(value = UmvuthanaSunLayer.class, remap = false)
public abstract class UmvuthanaSunLayerScanMixin {
    private static final String METHOD = "renderForBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/bobmowzie/mowziesmobs/server/entity/umvuthana/EntityUmvuthana;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V";

    @WrapOperation(method = METHOD, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85836_()V"), remap = false)
    private void bons$scanPush(PoseStack pose, Operation<Void> original, @Local(argsOnly = true) GeoBone bone, @Share("bons$boneSkip") LocalBooleanRef skip) {
        boolean s = LayerBoneScan.skip(bone, "head", null);
        skip.set(s);
        if (!s) original.call(pose);
    }

    @WrapOperation(method = METHOD, at = @At(value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/util/RenderUtils;translateToPivotPoint(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/core/animatable/model/CoreGeoBone;)V"), remap = false)
    private void bons$scanPivot(PoseStack pose, CoreGeoBone bone, Operation<Void> original, @Share("bons$boneSkip") LocalBooleanRef skip) {
        if (!skip.get()) original.call(pose, bone);
    }

    @WrapOperation(method = METHOD, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85849_()V"), remap = false)
    private void bons$scanPop(PoseStack pose, Operation<Void> original, @Share("bons$boneSkip") LocalBooleanRef skip) {
        if (!skip.get()) original.call(pose);
    }
}
