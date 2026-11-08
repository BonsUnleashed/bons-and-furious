package bons.furious.mixin.fluidium_coo49;

import bons.furious.patch.fluidium_coo49.ClaimMemo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fluidium_claim_lookup_memo (FTB Chunks 2101.1.23, server; part of the Fluidium claim memo; no FTB Chunks code carried).
 * At every return of ClaimedChunkManagerImpl.registerClaim and unregisterClaim (the only writers of its claim map besides
 * its constructor) the memo's claim epoch is bumped, so no answer kept before the change is used after it. Pseudo: Fluidium
 * runs without FTB Chunks, and then this class never exists. Priority 3000: applied after other mods' mixins on the class,
 * so FluidiumPlugin's look at the merged class sees them.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl", remap = false, priority = 3000)
public abstract class FtbClaimEpochMixin {
    @Inject(method = "registerClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;Ldev/ftb/mods/ftbchunks/api/ClaimedChunk;)V", at = @At("RETURN"), require = 1)
    private void bons$fluidiumClaimRegistered(CallbackInfo ci) {
        ClaimMemo.bump();
    }

    @Inject(method = "unregisterClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;)V", at = @At("RETURN"), require = 1)
    private void bons$fluidiumClaimUnregistered(CallbackInfo ci) {
        ClaimMemo.bump();
    }
}
