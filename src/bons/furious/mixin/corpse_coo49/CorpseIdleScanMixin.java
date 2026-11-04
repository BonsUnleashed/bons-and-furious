package bons.furious.mixin.corpse_coo49;

import bons.furious.patch.corpse_coo49.CorpseScans;
import de.maxhenkel.corpse.corelib.death.Death;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * corpse_idle_scan_streamless (Corpse 1.20.1-1.0.23, CorpseEntity; both sides, the tick that calls it runs on the logical
 * server). isMainInventoryEmpty and isAdditionalInventoryEmpty answer from plain loops over the same lists in the same
 * order (CorpseScans) instead of one stream().allMatch(ItemStack::isEmpty) per list; same answer, same isEmpty() calls,
 * no stream objects. No Corpse code is carried; the handlers are ours.
 */
@Mixin(targets = "de.maxhenkel.corpse.entities.CorpseEntity", remap = false)
public abstract class CorpseIdleScanMixin {
    @Shadow
    private Death death;

    @Inject(method = "isMainInventoryEmpty()Z", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$corpseCoo49MainEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (CorpseScans.enabled) cir.setReturnValue(CorpseScans.mainEmpty(this.death));
    }

    @Inject(method = "isAdditionalInventoryEmpty()Z", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$corpseCoo49AdditionalEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (CorpseScans.enabled) cir.setReturnValue(CorpseScans.additionalEmpty(this.death));
    }
}
