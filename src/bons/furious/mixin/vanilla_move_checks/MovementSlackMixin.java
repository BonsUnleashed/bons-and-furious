package bons.furious.mixin.vanilla_move_checks;

import bons.furious.patch.vanilla_move_checks.MoveChecks;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * vanilla_movement_check_slack (Minecraft 1.20.1 as patched by Forge 47.4.28, server side; kind change, off by default,
 * with a value). @ModifyConstant on the four thresholds of the movement checks, each matched exactly once: in
 * handleMovePlayer (m_7185_) the speed caps 300.0f (elytra) and 100.0f and the "moved wrongly" tolerance 0.0625; in
 * handleMoveVehicle (m_5659_) the speed cap 100.0 and the "moved wrongly" tolerance (the second 0.0625; the first and the
 * third deflate the vehicle's box for its collision tests and stay as they are). Each becomes the constant times the
 * configured multiplier. Only our own logic is carried. See MoveChecks.
 *
 * 1.0.49 fifth freeze (bug hunt F10): Too Fast 0.4.3.5 @ModifyConstant-s the three speed caps itself (to the largest
 * value, require 1, priority 1000). @ModifyConstant is a redirect-type injector: when two of them find the same constant,
 * Mixin gives it to the HIGHER priority and skips the lower one ("@ModifyConstant conflict. Skipping ..."), and a skipped
 * injector with require 1 stops the game. At equal priority the one prepared second lost: ours, which had require 1
 * ("Critical injection failure ... (0/1) succeeded"); at priority 1100 (tried first in the fifth freeze) Too Fast lost and
 * crashed instead. So this mixin has priority 900, below the default, and each handler has require = 0: a threshold
 * another mod's constant modifier also targets is left to it, the others keep the slack, and Guards.STAND_DOWN_EACH logs
 * one line per threshold left to another mod (naming it).
 */
@Mixin(value = ServerGamePacketListenerImpl.class, remap = false, priority = 900)
public abstract class MovementSlackMixin {
    @ModifyConstant(method = "m_7185_", require = 0, allow = 1, constant = @Constant(floatValue = 300.0f))
    private float bons$elytraSpeedCap(float cap) {
        return MoveChecks.cap(cap);
    }

    @ModifyConstant(method = "m_7185_", require = 0, allow = 1, constant = @Constant(floatValue = 100.0f))
    private float bons$playerSpeedCap(float cap) {
        return MoveChecks.cap(cap);
    }

    @ModifyConstant(method = "m_7185_", require = 0, allow = 1, constant = @Constant(doubleValue = 0.0625))
    private double bons$playerTolerance(double tolerance) {
        return MoveChecks.cap(tolerance);
    }

    @ModifyConstant(method = "m_5659_", require = 0, allow = 1, constant = @Constant(doubleValue = 100.0))
    private double bons$vehicleSpeedCap(double cap) {
        return MoveChecks.cap(cap);
    }

    @ModifyConstant(method = "m_5659_", require = 0, allow = 1, constant = @Constant(doubleValue = 0.0625, ordinal = 1))
    private double bons$vehicleTolerance(double tolerance) {
        return MoveChecks.cap(tolerance);
    }
}
