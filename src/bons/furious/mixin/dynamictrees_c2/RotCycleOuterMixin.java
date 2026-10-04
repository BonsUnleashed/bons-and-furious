package bons.furious.mixin.dynamictrees_c2;

import bons.furious.patch.dynamictrees_c2.RotCycleGuard;
import com.ferreusveritas.dynamictrees.tree.species.Species;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * dynamictrees_rot_cycle_guard (Dynamic Trees 1.20.1-1.4.11, both sides): Species.handleRot, the only caller of checkForRot in
 * Dynamic Trees (once per tree, never inside a rot cascade). Its start clears this thread's rot records, so a record an
 * exception left behind can never meet a later tree's cascade. Bookkeeping only; see RotCycleGuard. MIT target.
 */
@Mixin(value = Species.class, remap = false)
public abstract class RotCycleOuterMixin {
    @Inject(method = "handleRot", at = @At("HEAD"))
    private void bons$rotCycleOutermost(CallbackInfoReturnable<Boolean> cir) {
        RotCycleGuard.outermost();
    }
}
