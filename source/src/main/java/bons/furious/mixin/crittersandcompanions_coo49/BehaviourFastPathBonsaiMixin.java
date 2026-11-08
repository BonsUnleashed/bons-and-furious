package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.BehaviourFastPath;
import io.github.bonsaistudi0s.crittersandcompanions.common.entity.brain.behaviour.Behaviours;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * crittersandcompanions_behaviour_fast_path, Critters and Companions 2.7.0 for Minecraft 1.21.1 (brain.behaviour.Behaviours),
 * both sides. One HEAD injection on the(type) that answers directly for a plain Behaviours holder (runtime class exactly
 * Behaviours; a subclass keeps the original method); every other case runs the original method. forEach and optional keep
 * their own code (measured against the jar's class: an injection there was not a dependable gain, see BehaviourFastPath).
 */
@Mixin(value = Behaviours.class, remap = false)
public abstract class BehaviourFastPathBonsaiMixin {
    static {
        BehaviourFastPath.loaded();
    }

    @Shadow
    @Final
    private Map<?, ?> entries;

    @Inject(method = "the(Ljava/lang/Class;)Lio/github/bonsaistudi0s/crittersandcompanions/common/entity/brain/behaviour/Behaviour;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$the(Class<?> type, CallbackInfoReturnable<Object> cir) {
        if (BehaviourFastPath.enabled && ((Object) this).getClass() == Behaviours.class) {
            Object behaviour = this.entries.get(type);
            if (behaviour != null) cir.setReturnValue(behaviour);
        }
    }
}
