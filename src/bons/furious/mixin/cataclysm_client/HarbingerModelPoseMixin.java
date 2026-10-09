package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.ShieldLayerPose;
import com.github.L_Ender.cataclysm.client.model.entity.The_Harbinger_Model;
import com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Harbinger_Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cataclysm_shield_layer_pose (The_Harbinger_Model, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is carried; client).
 *
 * Records, when setupAnim returns, which model posed which entity with which five arguments (ShieldLayerPose.finished):
 * the shield layer then knows whether the renderer's model holds the pose its own call would produce.
 */
@Mixin(value = The_Harbinger_Model.class, remap = false)
public abstract class HarbingerModelPoseMixin {
    @Inject(method = "setupAnim(Lcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/The_Harbinger_Entity;FFFFF)V", at = @At("RETURN"))
    private void bons$poseFinished(The_Harbinger_Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci) {
        ShieldLayerPose.finished(this, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
