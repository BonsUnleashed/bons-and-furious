package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.CitadelPoseHand;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * citadel_pose_hand_events (Citadel, LGPL-3.0: no Citadel code is carried; 1.21.1 tested build citadel-2.7.1-1.21.1;
 * client).
 *
 * MixinSquared @TargetHandler: an @Inject at the HEAD of Citadel's merged HumanoidModelMixin.citadel_poseRightArm and
 * citadel_poseLeftArm handlers (in HumanoidModel.poseRightArm / poseLeftArm), cancellable. When CitadelPoseHand.skip says
 * the event would change nothing, Citadel's handler returns without building or posting its event and without cancelling
 * the arm pose; otherwise it runs as shipped. Priority 1500, require = 0, expect = 0 as in CataclysmPoseHandSkipMixin.
 *
 * Ported to 1.21.1: Citadel 2.7.1's handlers keep their names, descriptors and shape (post on NeoForge.EVENT_BUS, cancel on
 * TriState.TRUE); the injector is unchanged. The listeners 1.0.36 recognised (Alex's Caves, Alex's Mobs) have no NeoForge
 * 1.21.1 build, so CitadelPoseHand acts only while Citadel's event has no listener at all (see there).
 */
@Mixin(value = HumanoidModel.class, priority = 1500, remap = false)
public abstract class CitadelPoseHandSkipMixin {
    @TargetHandler(mixin = "com.github.alexthe666.citadel.mixin.client.HumanoidModelMixin", name = "citadel_poseRightArm")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$idleCitadelRightArmPoseEvent(LivingEntity entity, CallbackInfo theirCi, CallbackInfo ci) {
        if (CitadelPoseHand.skip(entity, (HumanoidModel<?>) (Object) this, false, theirCi)) ci.cancel();
    }

    @TargetHandler(mixin = "com.github.alexthe666.citadel.mixin.client.HumanoidModelMixin", name = "citadel_poseLeftArm")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$idleCitadelLeftArmPoseEvent(LivingEntity entity, CallbackInfo theirCi, CallbackInfo ci) {
        if (CitadelPoseHand.skip(entity, (HumanoidModel<?>) (Object) this, true, theirCi)) ci.cancel();
    }
}
