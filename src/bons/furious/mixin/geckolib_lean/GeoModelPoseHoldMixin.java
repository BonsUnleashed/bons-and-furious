package bons.furious.mixin.geckolib_lean;

import bons.furious.patch.geckolib_lean.PoseHold;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationProcessor;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * geckolib_distant_pose_hold (GeckoLib 4.8.4, client; since 1.0.49; trade-off, off by default): MixinExtras @WrapOperation
 * of the AnimationProcessor.tickAnimation call in GeoModel.handleAnimations; PoseHold either lets it run (and saves the
 * pose a small entity got) or writes that entity's own pose saved earlier in the same tick back instead. Everything else
 * in handleAnimations (time bookkeeping, AllTheLeaks' redirect of the controller_speed query, molang setup,
 * setCustomAnimations) runs as before. The saved poses live in a new field of the GeoModel. No GeckoLib code is carried.
 *
 * 1.0.49 fifth freeze (bug hunt R3-CRASH): GeckoLibOculusCompat 1.0.1 @Overwrites GeoModel.handleAnimations at the default
 * priority, and Mixin refuses an injector into a method replaced at the same or a higher priority before it looks at
 * require (a start crash). This mixin therefore has priority 1100 (Mixin lets it into the replacement) and the wrap has
 * require = 0: when the replacement has no such tickAnimation call the method is left to that mod, and
 * Guards.STAND_DOWN_EACH logs one line naming it.
 */
@Mixin(value = GeoModel.class, remap = false, priority = 1100)
public abstract class GeoModelPoseHoldMixin {
    @Unique
    private final PoseHold.Captures bons$poseCaptures = new PoseHold.Captures();

    @WrapOperation(method = "handleAnimations", require = 0, at = @At(value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/core/animation/AnimationProcessor;tickAnimation(Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/core/animatable/model/CoreGeoModel;Lsoftware/bernie/geckolib/core/animation/AnimatableManager;DLsoftware/bernie/geckolib/core/animation/AnimationState;Z)V"))
    private void bons$holdDistantPose(AnimationProcessor<?> processor, GeoAnimatable animatable, CoreGeoModel<?> model,
                                      AnimatableManager<?> manager, double animTime, AnimationState<?> state, boolean crash,
                                      Operation<Void> original, @Local(argsOnly = true) long instanceId) {
        PoseHold.tick(this.bons$poseCaptures, processor, animatable, model, manager, animTime, state, crash, original, instanceId);
    }
}
