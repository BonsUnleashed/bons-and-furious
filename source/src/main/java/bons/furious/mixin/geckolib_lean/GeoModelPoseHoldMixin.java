package bons.furious.mixin.geckolib_lean;

import bons.furious.patch.geckolib_lean.PoseHold;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * geckolib_distant_pose_hold (GeckoLib 4.9.3 for NeoForge 1.21.1, client; since 1.0.49; trade-off, off by default):
 * MixinExtras @WrapOperation of the AnimationProcessor.tickAnimation call in GeoModel.handleAnimations; PoseHold either lets
 * it run (and saves the pose a small entity got) or writes that entity's own pose saved earlier in the same tick back
 * instead. Everything else in handleAnimations (time bookkeeping, the molang setup, setCustomAnimations) runs as before. The
 * saved poses live in a new field of the GeoModel. No GeckoLib code is carried.
 *
 * Priority 1100 and require = 0 (as in Forge 1.0.49): a mod that @Overwrites GeoModel.handleAnimations at the default
 * priority lets this wrap into its replacement; when the replacement has no such tickAnimation call the method is left to
 * that mod, and Guards.STAND_DOWN_EACH logs one line naming it.
 */
@Mixin(value = GeoModel.class, remap = false, priority = 1100)
public abstract class GeoModelPoseHoldMixin {
    @Unique
    private final PoseHold.Captures bons$poseCaptures = new PoseHold.Captures();

    @WrapOperation(method = "handleAnimations", require = 0, at = @At(value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/animation/AnimationProcessor;tickAnimation(Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/model/GeoModel;Lsoftware/bernie/geckolib/animation/AnimatableManager;DLsoftware/bernie/geckolib/animation/AnimationState;Z)V"))
    private void bons$holdDistantPose(AnimationProcessor<?> processor, GeoAnimatable animatable, GeoModel<?> model,
                                      AnimatableManager<?> manager, double animTime, AnimationState<?> state, boolean crash,
                                      Operation<Void> original, @Local(argsOnly = true) long instanceId) {
        PoseHold.tick(this.bons$poseCaptures, processor, animatable, model, manager, animTime, state, crash, original, instanceId);
    }
}
