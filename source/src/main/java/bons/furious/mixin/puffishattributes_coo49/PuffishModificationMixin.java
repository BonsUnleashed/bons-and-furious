package bons.furious.mixin.puffishattributes_coo49;

import bons.furious.patch.puffishattributes_coo49.PuffishLean;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * puffish_attributes_empty_modifier_fast_path (Pufferfish's Attributes 0.8.3, LGPL-3.0; both sides): cancellable @Inject
 * at the HEAD of DynamicModificationImpl.applyTo(double), deciding through PuffishLean (our logic; nothing of the mod is
 * carried): when no entry has an ADD_VALUE, ADD_MULTIPLIED_BASE or ADD_MULTIPLIED_TOTAL modifier, the answer is the fourth pass alone
 * (each entry's sanitizeValue in order), which is what the original computes then; otherwise the original runs.
 */
@Mixin(targets = "net.puffish.attributesmod.util.DynamicModificationImpl", remap = false)
public abstract class PuffishModificationMixin {
    @Shadow
    @Final
    private List<?> attributes;

    @Inject(method = "applyTo(D)D", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$puffishattributesCoo49FastApply(double initial, CallbackInfoReturnable<Double> cir) {
        List<?> list = this.attributes;
        if (PuffishLean.allEmpty(list)) cir.setReturnValue(PuffishLean.sanitizeChain(list, initial));
    }
}
