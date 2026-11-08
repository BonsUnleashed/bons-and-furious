package bons.furious.mixin.geckolib_lean;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import software.bernie.geckolib.animation.AnimationController;

/**
 * geckolib_distant_pose_hold (GeckoLib 4.9.3 for NeoForge 1.21.1, client; since 1.0.49): read access to
 * AnimationController.tickOffset, the seek time the current stage started at, so PoseHold can tell whether the stage could
 * end before the next tick. Adds no behaviour.
 */
@Mixin(value = AnimationController.class, remap = false)
public interface AnimationControllerTickOffsetAccessor {
    @Accessor(value = "tickOffset", remap = false)
    double bons$tickOffset();
}
