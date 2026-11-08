package bons.furious.mixin.vanilla_move_checks;

import bons.furious.patch.vanilla_move_checks.MoveChecks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_flying_kick_off (Minecraft 1.21.1 with NeoForge 21.1.252, server side; kind change, off by default).
 * MixinExtras @ModifyExpressionValue on the one MinecraftServer.isFlightAllowed() call in handleMovePlayer and the one in
 * handleMoveVehicle, both inside the hover test that sets clientIsFloating / clientVehicleIsFloating, the flags tick()
 * counts toward the "Flying is not enabled" kick: the answer there is true (as with allow-flight=true), so the hover flags
 * stay false and the block scan after the test (noBlocksAround) is skipped. Every other isFlightAllowed() caller is
 * untouched. Only our own logic is carried. See MoveChecks.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, remap = false)
public abstract class FlyingKickMixin {
    @ModifyExpressionValue(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", allow = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isFlightAllowed()Z"))
    private boolean bons$playerHoverAllowed(boolean allowed) {
        return MoveChecks.flightAllowed(allowed);
    }

    @ModifyExpressionValue(method = "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V", allow = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isFlightAllowed()Z"))
    private boolean bons$vehicleHoverAllowed(boolean allowed) {
        return MoveChecks.flightAllowed(allowed);
    }
}
