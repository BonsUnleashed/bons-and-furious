package bons.furious.mixin.alltheleaks_c49;

import bons.furious.patch.alltheleaks_c49.AtlLean;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.lang.ref.WeakReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * alltheleaks_dead_ref_hash_spread (AllTheLeaks 1.1.3 / 1.1.4, MIT, both sides): MixinExtras @ModifyReturnValue on the
 * tracker set strategy's hashCode(WeakReference) (Trackable$1, the anonymous Hash.Strategy): a 0 from a dead reference
 * becomes the reference's identity hash (AtlLean.spreadDead); every other answer is returned unchanged.
 */
@Mixin(targets = "dev.uncandango.alltheleaks.mixin.Trackable$1", remap = false)
public abstract class TrackableStrategyMixin {
    @ModifyReturnValue(method = "hashCode(Ljava/lang/ref/WeakReference;)I", at = @At("RETURN"))
    private int bons$atlSpreadDead(int hash, @Local(argsOnly = true) WeakReference<?> ref) {
        return AtlLean.spreadDead(hash, ref);
    }
}
