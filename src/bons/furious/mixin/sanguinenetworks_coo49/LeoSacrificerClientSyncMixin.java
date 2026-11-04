package bons.furious.mixin.sanguinenetworks_coo49;

import bons.furious.patch.sanguinenetworks_coo49.SacrificerClientSync;
import com.leo.sanguine_networks.block.entity.VSBlockEntity;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * sanguinenetworks_leo_client_sync_no_remesh (Sanguine Neural Networks 1.2.1, package com.leo, client side in effect; exact
 * variant; no Sanguine code is carried). As sanguinenetworks_client_sync_no_remesh for the 1.x block entity (whose sync()
 * calls sendBlockUpdated without a null check; a null level still fails there as before). See SacrificerClientSync.
 */
@Mixin(value = VSBlockEntity.class, remap = false)
public abstract class LeoSacrificerClientSyncMixin {
    @WrapWithCondition(method = "sync()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7260_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V"),
            require = 1, allow = 1)
    private boolean bons$sanguineLeoClientNoRemesh(Level level, BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        if (level != null && level.f_46443_ && SacrificerClientSync.leoEnabled) {
            SacrificerClientSync.skipped(true);
            return false;
        }
        return true;
    }
}
