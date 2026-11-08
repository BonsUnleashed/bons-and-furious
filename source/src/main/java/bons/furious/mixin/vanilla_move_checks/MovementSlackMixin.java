package bons.furious.mixin.vanilla_move_checks;

import bons.furious.patch.vanilla_move_checks.MoveChecks;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * vanilla_movement_check_slack (Minecraft 1.21.1 with NeoForge 21.1.252, server side; kind change, off by default, with a
 * value). @ModifyConstant on the four thresholds of the movement checks, each matched exactly once: in handleMovePlayer the
 * speed caps 300.0f (gliding with an elytra) and 100.0f and the "moved wrongly" tolerance 0.0625; in handleMoveVehicle the
 * speed cap 100.0 and the "moved wrongly" tolerance (the second 0.0625; the first and the third deflate the vehicle's box
 * for its two noCollision tests and stay as they are). Each becomes the constant times the configured multiplier. The
 * hover test's -0.03125, the clamps and every collision check keep their numbers. Only our own logic is carried. See
 * MoveChecks.
 *
 * A constant modifier is a redirect-type injector: when two of them find the same constant, Mixin gives it to the HIGHER
 * priority and skips the lower one ("@ModifyConstant conflict. Skipping ..."), and a skipped injector with require 1 stops
 * the game. So this mixin has priority 900, below the default 1000, and each handler has require = 0: a threshold another
 * mod's constant modifier also targets (Too Fast does this for the three speed caps on Forge) is left to that mod, the
 * others keep the slack, and Guards.STAND_DOWN_EACH logs one line per threshold left to another mod (naming it).
 */
@Mixin(value = ServerGamePacketListenerImpl.class, remap = false, priority = 900)
public abstract class MovementSlackMixin {
    @ModifyConstant(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", require = 0, allow = 1,
            constant = @Constant(floatValue = 300.0f))
    private float bons$elytraSpeedCap(float cap) {
        return MoveChecks.cap(cap);
    }

    @ModifyConstant(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", require = 0, allow = 1,
            constant = @Constant(floatValue = 100.0f))
    private float bons$playerSpeedCap(float cap) {
        return MoveChecks.cap(cap);
    }

    @ModifyConstant(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", require = 0, allow = 1,
            constant = @Constant(doubleValue = 0.0625))
    private double bons$playerTolerance(double tolerance) {
        return MoveChecks.cap(tolerance);
    }

    @ModifyConstant(method = "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V", require = 0, allow = 1,
            constant = @Constant(doubleValue = 100.0))
    private double bons$vehicleSpeedCap(double cap) {
        return MoveChecks.cap(cap);
    }

    @ModifyConstant(method = "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V", require = 0, allow = 1,
            constant = @Constant(doubleValue = 0.0625, ordinal = 1))
    private double bons$vehicleTolerance(double tolerance) {
        return MoveChecks.cap(tolerance);
    }
}
