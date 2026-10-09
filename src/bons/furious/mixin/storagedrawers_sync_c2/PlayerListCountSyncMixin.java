package bons.furious.mixin.storagedrawers_sync_c2;

import bons.furious.patch.storagedrawers_sync_c2.CountSyncHolders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * storagedrawers_count_sync_holders (applies only with Storage Drawers 1.20.1-12.15.1 installed, server): the per-player send
 * in PlayerList.broadcast(Player, x, y, z, radius, dimension, packet) (m_11241_). Outside a Storage Drawers count send this
 * is one static field read per recipient; inside one, a player whose client cannot hold the drawer's chunk is skipped (see
 * CountSyncHolders). The loop, the distance test and other mods' injections into broadcast are untouched. require = 0: if
 * another mod replaces the call, the filter is simply never consulted (the original behaviour). Minecraft (Mojang): our own
 * logic only.
 *
 * Priority 1500 (since 1.0.36). Mixin refuses an injector into a method that another mod's mixin of the same or a higher
 * priority has replaced with an @Overwrite, and it does so before it reads require: at the default priority a mod that
 * replaces broadcast stopped the server at start ("cannot inject into ... merged by ... with priority 1000"; reported with
 * Sable 2.0.6 on NeoForge 1.21.1). Above the default, Mixin lets this wrap into the replacement, and the switch steps aside:
 * Guards.checkStandDown (postApply) sees broadcast merged by that mod's mixin, sets Guards.countSyncBroadcastForeign, so
 * CountSyncHolders.send runs Storage Drawers' count sends exactly as stock and this wrap hands every call on, and logs one
 * line naming the mod. Without such a mod the transformed PlayerList is the 1.0.35 one apart from the priority and the
 * source line numbers Mixin records on this handler.
 */
@Mixin(value = PlayerList.class, remap = false, priority = 1500)
public abstract class PlayerListCountSyncMixin {
    @WrapOperation(method = "m_11241_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;m_9829_(Lnet/minecraft/network/protocol/Packet;)V"))
    private void bons$countSyncRecipient(ServerGamePacketListenerImpl connection, Packet<?> packet, Operation<Void> original) {
        if (CountSyncHolders.keep(connection, packet)) {
            original.call(connection, packet);
        }
    }
}
