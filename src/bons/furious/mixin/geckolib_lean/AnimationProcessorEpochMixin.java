package bons.furious.mixin.geckolib_lean;

import bons.furious.patch.geckolib_lean.PoseHoldEpoch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.core.animatable.model.CoreBakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationProcessor;

/**
 * geckolib_distant_pose_hold (GeckoLib 4.8.4, client; since 1.0.49): counts AnimationProcessor.setActiveModel calls (a new
 * field, read through PoseHoldEpoch), so a pose saved for one baked model is never written back after the processor
 * switched models. setActiveModel itself runs unchanged.
 */
@Mixin(value = AnimationProcessor.class, remap = false)
public abstract class AnimationProcessorEpochMixin implements PoseHoldEpoch {
    @Unique
    private int bons$epoch;

    @Inject(method = "setActiveModel", at = @At("HEAD"))
    private void bons$countModelSwitch(CoreBakedGeoModel model, CallbackInfo ci) {
        this.bons$epoch++;
    }

    @Override
    public int bons$poseEpoch() {
        return this.bons$epoch;
    }
}
