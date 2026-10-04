package bons.furious.mixin.storagedrawers_sync_c2;

import bons.furious.patch.storagedrawers_sync_c2.CountSyncHolders;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.texelsaurus.minecraft.chameleon.network.ChameleonPacket;
import com.texelsaurus.minecraft.chameleon.service.ChameleonNetworking;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * storagedrawers_count_sync_holders (Storage Drawers, MIT; 1.21.1 tested build: StorageDrawers-neoforge-1.21.1-13.11.4;
 * server): the standard drawers' count send (BlockEntityDrawers.syncClientCount). The original sendToPlayersNear call runs
 * unchanged, inside CountSyncHolders' scope that lets PlayerList.broadcast skip players whose client cannot hold the
 * drawer's chunk. MIT target, no code carried.
 * Ported to 1.21.1: same call; ChameleonPacket is no longer generic (it is a NeoForge CustomPacketPayload).
 */
@Mixin(value = BlockEntityDrawers.class, remap = false)
public abstract class DrawerCountSyncMixin {
    @WrapOperation(method = "syncClientCount(II)V", at = @At(value = "INVOKE",
            target = "Lcom/texelsaurus/minecraft/chameleon/service/ChameleonNetworking;sendToPlayersNear(Lcom/texelsaurus/minecraft/chameleon/network/ChameleonPacket;Lnet/minecraft/server/level/ServerLevel;DDDD)V"))
    private void bons$countSync(ChameleonNetworking network, ChameleonPacket packet, ServerLevel level, double x, double y, double z,
                                double radius, Operation<Void> original) {
        CountSyncHolders.send(network, packet, level, x, y, z, radius, original);
    }
}
