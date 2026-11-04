package bons.furious.mixin.refinedmod_coo49;

import bons.furious.patch.refinedmod_coo49.RefinedFixes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * refinedmod_wave_terraforming_off (Refined Mod 1.1.6, All Rights Reserved; both sides; since 1.0.49; change, off by
 * default): CustomWaveEntity.extinguishNearbyFires and convertLavaToCobblestone return at their head on every level, so
 * tidal waves no longer clear fire or turn lava into cobblestone. Helper RefinedFixes.
 */
@Mixin(targets = "net.refinedrain.refinedmod.entity.spells.CustomWaveEntity", remap = false)
public abstract class RefinedWaveTerraformingOffMixin {
    @Inject(method = {"extinguishNearbyFires()V", "convertLavaToCobblestone()V"}, at = @At("HEAD"), cancellable = true, require = 2, allow = 2)
    private void bons$refinedmodCoo49TerraformingOff(CallbackInfo ci) {
        if (RefinedFixes.skipSweep(null, false)) ci.cancel();
    }
}
