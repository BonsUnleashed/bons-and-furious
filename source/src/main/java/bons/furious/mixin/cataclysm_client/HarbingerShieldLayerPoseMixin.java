package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.ShieldLayerPose;
import com.github.L_Ender.cataclysm.client.render.layer.The_Harbinger_Shield_Layer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_shield_layer_pose (The_Harbinger_Shield_Layer, L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is
 * carried; 1.21.1 tested build L_Ender's Cataclysm 1.21.1-3.33; client).
 *
 * While the Harbinger is powered, its shield layer runs prepareMobModel (EntityModel's empty default), copyPropertiesTo
 * (onto itself) and setupAnim again on the renderer's own model with the renderer's arguments. When that model has just
 * finished setupAnim for this entity with the same arguments it already holds the pose, so the repeated setupAnim is not
 * made (ShieldLayerPose.alreadyPosed); otherwise, and in shadow mode, it runs as shipped.
 *
 * Ported to 1.21.1: The_Harbinger_Shield_Layer.render in 3.33 keeps its descriptor and its prepareMobModel /
 * copyPropertiesTo / EntityModel.setupAnim / renderToBuffer order (Lionfish 3.1's BasicEntityModel.prepareMobModel is
 * still empty); the wrapped call is unchanged.
 */
@Mixin(value = The_Harbinger_Shield_Layer.class, remap = false)
public abstract class HarbingerShieldLayerPoseMixin {
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/The_Harbinger_Entity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void bons$poseAlreadyThere(EntityModel<?> model, Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch, Operation<Void> original) {
        float[] before = ShieldLayerPose.shadowWanted(model, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)
                ? ShieldLayerPose.shadowSnapshot(model) : null;
        if (ShieldLayerPose.alreadyPosed(model, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)) return;
        original.call(model, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (before != null) ShieldLayerPose.shadowCompareSnapshot(before, model, entity);
    }
}
