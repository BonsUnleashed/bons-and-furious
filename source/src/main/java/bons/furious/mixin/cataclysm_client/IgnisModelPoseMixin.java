package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.ShieldLayerPose;
import com.github.L_Ender.cataclysm.client.model.entity.Ignis_Model;
import com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity;
import com.github.L_Ender.lionfishapi.client.model.Animations.ModelAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cataclysm_shield_layer_pose (Ignis_Model, L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1 tested
 * build L_Ender's Cataclysm 1.21.1-3.33; client).
 *
 * Records, when setupAnim returns, which model posed which entity with which five arguments (ShieldLayerPose.finished):
 * the shield layer then knows whether the renderer's model holds the pose its own call would produce. Also hands the shield layer
 * this model's ModelAnimator (ShieldLayerPose.Animated).
 *
 * Ported to 1.21.1: same typed setupAnim descriptor and private animator field in 3.33; nothing changed.
 */
@Mixin(value = Ignis_Model.class, remap = false)
public abstract class IgnisModelPoseMixin implements ShieldLayerPose.Animated {
    @Shadow
    private ModelAnimator animator;

    @Override
    public ModelAnimator bons$animator() {
        return this.animator;
    }

    @Inject(method = "setupAnim(Lcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/Ignis_Entity;FFFFF)V", at = @At("RETURN"))
    private void bons$poseFinished(Ignis_Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci) {
        ShieldLayerPose.finished(this, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
