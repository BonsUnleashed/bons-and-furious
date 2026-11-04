package bons.furious.mixin.cognition_coo49;

import bons.furious.patch.cognition_coo49.InfectionSkip;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cognition_client_infection_skip (Cognition 2.4.12, client; exact variant; no Cognition code carried).
 * AbstractInfectiveEntity.infectAdjacent returns at once on a client level, where its search can only end in
 * BibliophageItem.infectBlock's "if (!level.isClientSide)". See InfectionSkip.
 */
@Mixin(targets = "com.cyanogen.experienceobelisk.block_entities.bibliophage.AbstractInfectiveEntity", remap = false)
public abstract class InfectAdjacentClientMixin {
    @Inject(method = "infectAdjacent(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void bons$cognitionClientInfection(Level level, BlockPos pos, CallbackInfo ci) {
        if (InfectionSkip.skip(level)) ci.cancel();
    }
}
