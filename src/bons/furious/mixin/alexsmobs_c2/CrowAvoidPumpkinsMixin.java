package bons.furious.mixin.alexsmobs_c2;

import bons.furious.patch.alexsmobs_c2.CrowPumpkinScan;
import com.github.alexthe666.alexsmobs.entity.EntityCrow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * alexsmobs_crow_scan_palette_check (Alex's Mobs 1.22.9, LGPL; both sides, the goal runs on the server), part 1 of 2.
 *
 * A head injection into Alex's Mobs' own EntityCrow$AIAvoidPumpkins.searchForDestination (16,731 block reads looking for
 * alexsmobs:crow_fears around a wild crow): when CrowPumpkinScan shows from the loaded chunks' section palettes that no
 * read can match (and has replayed the scan's chunk lookups), the search returns false as it would have, leaving the
 * destination untouched; otherwise Alex's Mobs' search runs unchanged. Vanilla MoveToBlockGoal (used by the crow's crop
 * goal) is not touched.
 */
@Mixin(targets = "com.github.alexthe666.alexsmobs.entity.EntityCrow$AIAvoidPumpkins", remap = false)
public abstract class CrowAvoidPumpkinsMixin {
    @Shadow
    @Final
    EntityCrow this$0;

    @Inject(method = "searchForDestination", at = @At("HEAD"), cancellable = true)
    private void bons$nothingToFear(CallbackInfoReturnable<Boolean> cir) {
        if (CrowPumpkinScan.noneFeared(this.this$0)) cir.setReturnValue(false);
    }
}
