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
 * storagedrawers_count_sync_holders (applies only with Storage Drawers installed; 1.21.1 tested build:
 * StorageDrawers-neoforge-1.21.1-13.11.4 with NeoForge 21.1.252; server): the per-player send in
 * PlayerList.broadcast(Player, x, y, z, radius, dimension, packet). Outside a Storage Drawers count send this is one static
 * field read per recipient; inside one, a player whose client cannot hold the drawer's chunk is skipped (see
 * CountSyncHolders). The loop, the distance test and other mods' injections into broadcast are untouched. require = 0: if
 * another mod replaces the call, the filter is simply never consulted (the original behaviour). Minecraft (Mojang): our own
 * logic only.
 * Ported to 1.21.1: PlayerList.broadcast is the same loop; the call is still ServerGamePacketListenerImpl.send(Packet)
 * (declared in ServerCommonPacketListenerImpl since 1.20.2, referenced through the field's type).
 *
 * Priority 1500 (since 1.0.38+mc1.21.1): a mod that replaces broadcast with an @Overwrite at the default 1000 (Sable
 * 2.0.6, the physics library of Create Aeronautics: its plot.PlayerListMixin measures the distance to sub-levels) made
 * Mixin refuse this wrap at equal priority, and PlayerList failed to load at the server start whenever Storage Drawers was
 * installed too (require = 0 does not help: the refusal comes first). Above that priority the wrap is let in; Guards'
 * postApply check (FOREIGN_OVERWRITE) then sees broadcast merged by the other mod, and the switch stands down: the
 * count sends run without the filter and the other mod's loop sends exactly as it ships. One line names that mod.
 */
@Mixin(value = PlayerList.class, remap = false, priority = 1500)
public abstract class PlayerListCountSyncMixin {
    @WrapOperation(method = "broadcast", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void bons$countSyncRecipient(ServerGamePacketListenerImpl connection, Packet<?> packet, Operation<Void> original) {
        if (CountSyncHolders.keep(connection, packet)) {
            original.call(connection, packet);
        }
    }
}
