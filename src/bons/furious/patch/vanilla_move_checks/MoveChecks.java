package bons.furious.patch.vanilla_move_checks;

import bons.pure.config.PureConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Bons and Furious switches vanilla_flying_kick_off and vanilla_movement_check_slack (Minecraft 1.20.1 as patched by Forge
 * 47.4.28, server side incl. the integrated server). Both are trade-offs (kind change, off by default): with the config
 * key off the mixins are not applied and ServerGamePacketListenerImpl runs as shipped; the runtime flags below are only the
 * emergency off while a key is on. No Mojang code here.
 *
 * vanilla_flying_kick_off. handleMovePlayer (m_7185_) and handleMoveVehicle (m_5659_) end an accepted move by working out
 * whether the player (or the vehicle they drive) hovers without support: a chain of checks that ends with
 * !server.isFlightAllowed() (m_129915_) and then a block scan around the entity (m_9793_). tick() kicks the player with
 * "Flying is not enabled on this server" once that answer stayed true for 80 ticks. With the switch on, the
 * isFlightAllowed() answer read in those two places only is true, as it is on a server with allow-flight=true: the hover
 * flags stay false, the block scan is skipped (the chain stops at that test) and the kick never arms. Nothing else
 * reads those flags; every other isFlightAllowed() caller is unchanged.
 *
 * vanilla_movement_check_slack. The same two handlers reject a move as "moved too quickly" when its squared length beats
 * a cap (100, or 300 while flying with an elytra, per packet since the last tick, in handleMovePlayer; 100 in
 * handleMoveVehicle) and flag it as "moved wrongly" when the server's own simulation of the move lands more than
 * sqrt(0.0625) = 0.25 blocks from where the client said. With the switch on those four numbers are multiplied by the
 * value (default 100, COO's default: up to 100 blocks per tick and 2.5 blocks of disagreement before a correction; 1 =
 * vanilla). Only those constants change: the collision checks (noCollision, the new-collision test, the vehicle's
 * deflated box) keep their own numbers, so a client still cannot move into blocks.
 *
 * -Dbons_and_furious.flyingKickOff=false / -Dbons_and_furious.movementCheckSlack=false turn the changed answers back off
 * at run time while the keys are on.
 */
public final class MoveChecks {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean flyingKickOff = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.flyingKickOff", "true"));
    public static volatile boolean slack = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.movementCheckSlack", "true"));
    private static volatile boolean announcedKick;

    private MoveChecks() {
    }

    /** The multiplier, read once on the switch-on path (the mixin that calls it exists only while the key is on). */
    private static final class Slack {
        static final double VALUE = PureConfig.doubleValue("vanilla_movement_check_slack", 100.0, 1.0, 1000.0);

        static {
            LOGGER.info("Bons and Furious: vanilla_movement_check_slack applies (movement speed caps and the move tolerance x {})", VALUE);
        }
    }

    /** The isFlightAllowed() answer in the two movement handlers' hover test. */
    public static boolean flightAllowed(boolean allowed) {
        if (!flyingKickOff || allowed) return allowed;
        if (!announcedKick) {
            announcedKick = true;
            LOGGER.info("Bons and Furious: vanilla_flying_kick_off applies (no \"Flying is not enabled\" kick for hovering)");
        }
        return true;
    }

    /** A float speed cap of handleMovePlayer (100 or 300). */
    public static float cap(float vanilla) {
        return slack ? (float) (vanilla * Slack.VALUE) : vanilla;
    }

    /** handleMoveVehicle's speed cap (100) or a move tolerance (0.0625). */
    public static double cap(double vanilla) {
        return slack ? vanilla * Slack.VALUE : vanilla;
    }

    /** For the proof harness: the multiplier in use. */
    public static double value() {
        return Slack.VALUE;
    }
}
