package bons.furious.mixin.goetyhostility_coo49;

import bons.furious.patch.goetyhostility_coo49.GhScans;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * goetyhostility_wildfire_drop_probe_lean (Goety Hostility 1.4.4, all rights reserved: no code carried; server; exact).
 * HostileWildfire.steepDropBelow reads all eight block states below the boss although the first non-air one settles the
 * answer; the probe here stops there with one mutable position (GhScans.steepDropBelow: the same answer, the same first
 * read).
 */
@Mixin(targets = "com.ratrod.goetyhostility.common.entities.hostile.HostileWildfire", remap = false)
public abstract class GhWildfireDropProbeMixin {
    @Inject(method = "steepDropBelow()Z", at = @At("HEAD"), cancellable = true)
    private void bons$goetyhostilityDropProbe(CallbackInfoReturnable<Boolean> cir) {
        if (GhScans.probe) cir.setReturnValue(GhScans.steepDropBelow((Entity) (Object) this));
    }
}
