package bons.furious.mixin.sanguinenetworks_coo49;

import bons.furious.patch.sanguinenetworks_coo49.SacrificerClientSync;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.luna.sanguine_networks.block.entity.VSBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * sanguinenetworks_client_sync_no_remesh (Sanguine Neural Networks 2.0.1, client side in effect; exact variant; no Sanguine
 * code is carried). VSBlockEntity.sync() on a client level marks the block entity changed but leaves out the same-state
 * sendBlockUpdated that would only mesh identical chunk sections again. See SacrificerClientSync.
 */
@Mixin(value = VSBlockEntity.class, remap = false)
public abstract class SacrificerClientSyncMixin {
    @WrapWithCondition(method = "sync()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7260_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V"),
            require = 1, allow = 1)
    private boolean bons$sanguineClientNoRemesh(Level level, BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        if (level != null && level.f_46443_ && SacrificerClientSync.enabled) {
            SacrificerClientSync.skipped(false);
            return false;
        }
        return true;
    }
}
