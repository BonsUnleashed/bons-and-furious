package bons.furious.mixin.refinedmod_coo49;

import bons.furious.patch.refinedmod_coo49.RefinedFixes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * refinedmod_wave_terrain_scan_lean (Refined Mod 1.1.6, All Rights Reserved; both sides; since 1.0.49; change, off by
 * default): each CustomWaveEntity remembers the block it stood in when each of its two terrain sweeps last ran to the end
 * (RETURN), and the sweep returns at its head while the wave is still in that block (the cube it would read is the cube it
 * read last time). Helper RefinedFixes.
 */
@Mixin(targets = "net.refinedrain.refinedmod.entity.spells.CustomWaveEntity", remap = false)
public abstract class RefinedWaveLeanScanMixin {
    @Unique
    private boolean bons$refinedmodCoo49FireSwept, bons$refinedmodCoo49LavaSwept;
    @Unique
    private long bons$refinedmodCoo49FireBlock, bons$refinedmodCoo49LavaBlock;

    @Inject(method = "extinguishNearbyFires()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$refinedmodCoo49LeanFireHead(CallbackInfo ci) {
        if (bons$refinedmodCoo49FireSwept && RefinedFixes.sameBlock((Entity) (Object) this, bons$refinedmodCoo49FireBlock)) ci.cancel();
    }

    @Inject(method = "extinguishNearbyFires()V", at = @At("RETURN"), require = 1)
    private void bons$refinedmodCoo49LeanFireDone(CallbackInfo ci) {
        if (!RefinedFixes.leanTerrainScan) return;
        bons$refinedmodCoo49FireBlock = RefinedFixes.sweepBlock((Entity) (Object) this);
        bons$refinedmodCoo49FireSwept = true;
    }

    @Inject(method = "convertLavaToCobblestone()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$refinedmodCoo49LeanLavaHead(CallbackInfo ci) {
        if (bons$refinedmodCoo49LavaSwept && RefinedFixes.sameBlock((Entity) (Object) this, bons$refinedmodCoo49LavaBlock)) ci.cancel();
    }

    @Inject(method = "convertLavaToCobblestone()V", at = @At("RETURN"), require = 1)
    private void bons$refinedmodCoo49LeanLavaDone(CallbackInfo ci) {
        if (!RefinedFixes.leanTerrainScan) return;
        bons$refinedmodCoo49LavaBlock = RefinedFixes.sweepBlock((Entity) (Object) this);
        bons$refinedmodCoo49LavaSwept = true;
    }
}
