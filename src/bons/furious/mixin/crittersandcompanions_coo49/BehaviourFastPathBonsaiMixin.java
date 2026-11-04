package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.BehaviourFastPath;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * crittersandcompanions_behaviour_fast_path, Critters and Companions 2.7.1 (brain.behaviour.Behaviours; not on the build's
 * class path: string target + @Pseudo, offered to Mixin by CrittersandcompanionsPlugin only when the guard matched the 2.7.1
 * classes), both sides. One HEAD injection on the(type) that answers directly for a plain Behaviours holder; every other
 * case runs the original method. forEach and optional keep their own code (measured against the jar's class: an injection
 * there was not a dependable gain, see BehaviourFastPath).
 */
@Pseudo
@Mixin(targets = "io.github.bonsaistudi0s.crittersandcompanions.common.entity.brain.behaviour.Behaviours", remap = false)
public abstract class BehaviourFastPathBonsaiMixin {
    /** The target class itself (this initializer runs inside the target's static initializer): subclasses keep the originals. */
    @Unique
    private static final Class<?> bons$PLAIN;

    static {
        bons$PLAIN = MethodHandles.lookup().lookupClass();
        BehaviourFastPath.loaded();
    }

    @Shadow
    @Final
    private Map<?, ?> entries;

    @Inject(method = "the", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$the(Class<?> type, CallbackInfoReturnable<Object> cir) {
        if (BehaviourFastPath.enabled && ((Object) this).getClass() == bons$PLAIN) {
            Object behaviour = this.entries.get(type);
            if (behaviour != null) cir.setReturnValue(behaviour);
        }
    }
}
