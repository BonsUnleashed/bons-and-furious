package bons.furious.patch.geckolib_lean;

/**
 * geckolib_distant_pose_hold (since 1.0.49): implemented by GeckoLib's AnimationProcessor through
 * bons.furious.mixin.geckolib_lean.AnimationProcessorEpochMixin; the number of setActiveModel calls the processor has seen,
 * so a pose captured for one baked model is never restored after the processor switched to another.
 */
public interface PoseHoldEpoch {
    int bons$poseEpoch();
}
