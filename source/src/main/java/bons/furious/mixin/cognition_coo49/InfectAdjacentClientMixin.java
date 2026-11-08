package bons.furious.mixin.cognition_coo49;

import bons.furious.patch.cognition_coo49.InfectionSkip;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cognition_client_infection_skip (Cognition, MIT; 1.21.1 tested build: Cognition 2.4.13 for NeoForge 1.21.1,
 * Cognition-v2.4.13-1.21.1.jar; client; exact variant; no Cognition code carried). AbstractInfectiveEntity.infectAdjacent
 * returns at once on a client level, where its search can only end in BibliophageItem.infectBlock's
 * "if (!level.isClientSide)". See InfectionSkip.
 *
 * Ported to 1.21.1: the mod's package is com.cyanogen.cognition (com.cyanogen.experienceobelisk in 2.4.12 for 1.20.1); the
 * method keeps its body: six neighbour reads, getInfectedBlockState per neighbour, one Math.random() pick, infectBlock.
 */
@Mixin(targets = "com.cyanogen.cognition.block_entities.bibliophage.AbstractInfectiveEntity", remap = false)
public abstract class InfectAdjacentClientMixin {
    @Inject(method = "infectAdjacent(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void bons$cognitionClientInfection(Level level, BlockPos pos, CallbackInfo ci) {
        if (InfectionSkip.skip(level)) ci.cancel();
    }
}
