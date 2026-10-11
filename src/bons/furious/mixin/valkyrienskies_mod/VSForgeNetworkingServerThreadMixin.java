package bons.furious.mixin.valkyrienskies_mod;

import bons.furious.patch.valkyrienskies_mod.VsPacketsServerThread;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.internal.hooks.VsiCoreHooksIn;
import org.valkyrienskies.mod.forge.common.MessageVSPacket;
import org.valkyrienskies.mod.forge.common.VSForgeNetworking;

/**
 * valkyrien_packets_server_thread (Valkyrien Skies 2.4.11, Forge).
 *
 * VSForgeNetworking registers one SimpleChannel consumer (registerPacketHandlers$lambda$3) for every Valkyrien Skies
 * packet. Forge calls it on the Netty thread that received the packet, and for a packet from a player it passes the
 * bytes straight to Valkyrien Skies core (onReceiveServer), which runs the packet's handlers on that thread too. One of
 * them, the standing-on-a-ship packet, moves the player (ServerPlayer.setPos). The helper copies the packet's bytes and
 * runs the same onReceiveServer call on the server thread instead (VsPacketsServerThread). Packets the client receives
 * (no sender) and a call that already runs on the server thread keep Valkyrien Skies' own path.
 */
@Mixin(value = VSForgeNetworking.class, remap = false)
public abstract class VSForgeNetworkingServerThreadMixin {
    @Inject(method = "registerPacketHandlers$lambda$3", at = @At("HEAD"), cancellable = true)
    private static void bons$serverThread(VsiCoreHooksIn hooks, MessageVSPacket packet, Supplier<NetworkEvent.Context> context,
                                          CallbackInfo ci) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null && VsPacketsServerThread.defer(hooks, packet.getBuf(), sender)) {
            ctx.setPacketHandled(true);
            ci.cancel();
        }
    }
}
