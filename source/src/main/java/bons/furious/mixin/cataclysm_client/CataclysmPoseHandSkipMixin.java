package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.CataclysmPoseHand;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cataclysm_pose_hand_events (L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1 tested build
 * L_Ender's Cataclysm 1.21.1-3.33; client).
 *
 * MixinSquared @TargetHandler: an @Inject at the HEAD of Cataclysm's merged HumanoidModelMixin.custom_poseRightArm and
 * custom_poseLeftArm handlers (in HumanoidModel.poseRightArm / poseLeftArm), cancellable. When CataclysmPoseHand.skip says
 * the event would change nothing, our CallbackInfo is cancelled: Cataclysm's handler returns without building or posting
 * its event and without cancelling the arm pose (as it does when no listener acts); otherwise it runs as shipped.
 * Priority 1500: applied after Cataclysm's mixin, whose handlers must already be merged. require = 0, expect = 0: without
 * Cataclysm's handler the class transforms with nothing of ours in it (KNOWLEDGE 2026-10-03, MixinSquared facts).
 *
 * Ported to 1.21.1: Cataclysm 3.33's handlers keep their names, descriptors and shape (build the event, post it on
 * NeoForge.EVENT_BUS, cancel on its own Result.ALLOW); the injector is unchanged, the listener check moved to the NeoForge
 * bus (PoseHandEvents).
 */
@Mixin(value = HumanoidModel.class, priority = 1500, remap = false)
public abstract class CataclysmPoseHandSkipMixin {
    @TargetHandler(mixin = "com.github.L_Ender.cataclysm.mixin.Client.HumanoidModelMixin", name = "custom_poseRightArm")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$idleRightArmPoseEvent(LivingEntity entity, CallbackInfo theirCi, CallbackInfo ci) {
        if (CataclysmPoseHand.skip(entity, (HumanoidModel<?>) (Object) this, false, theirCi)) ci.cancel();
    }

    @TargetHandler(mixin = "com.github.L_Ender.cataclysm.mixin.Client.HumanoidModelMixin", name = "custom_poseLeftArm")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$idleLeftArmPoseEvent(LivingEntity entity, CallbackInfo theirCi, CallbackInfo ci) {
        if (CataclysmPoseHand.skip(entity, (HumanoidModel<?>) (Object) this, true, theirCi)) ci.cancel();
    }
}
