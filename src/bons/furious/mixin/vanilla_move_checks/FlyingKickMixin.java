package bons.furious.mixin.vanilla_move_checks;

import bons.furious.patch.vanilla_move_checks.MoveChecks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_flying_kick_off (Minecraft 1.20.1 as patched by Forge 47.4.28, server side; kind change, off by default).
 * MixinExtras @ModifyExpressionValue on the one MinecraftServer.isFlightAllowed() (m_129915_) call in handleMovePlayer
 * (m_7185_) and the one in handleMoveVehicle (m_5659_), both inside the hover test that arms the 80-tick "flying" kick:
 * the answer there is true (as with allow-flight=true), so the hover flags stay false and the block scan after the test
 * is skipped. Every other isFlightAllowed() caller is untouched. Only our own logic is carried. See MoveChecks.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, remap = false)
public abstract class FlyingKickMixin {
    @ModifyExpressionValue(method = "m_7185_", allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;m_129915_()Z"))
    private boolean bons$playerHoverAllowed(boolean allowed) {
        return MoveChecks.flightAllowed(allowed);
    }

    @ModifyExpressionValue(method = "m_5659_", allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;m_129915_()Z"))
    private boolean bons$vehicleHoverAllowed(boolean allowed) {
        return MoveChecks.flightAllowed(allowed);
    }
}
