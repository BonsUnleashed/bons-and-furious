package bons.furious.patch.amendments_sway;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch amendments_idle_sway_packet_skip (Amendments 1.21-2.2.7 for NeoForge, Supplementaries Team License:
 * none of its code is carried; server side incl. the integrated server; since 1.0.49). Trade-off (off by default).
 * Idea: Collections Of Optimizations (amendments.skipIdleSwaySync), idea text only.
 *
 * What Amendments does. WallLanternBlock.entityInside runs every tick for every entity whose box overlaps a wall lantern's
 * block cell (the cell, not the lantern's shape: Entity.checkInsideBlocks visits every cell the box touches). On the server
 * it raises a game event when the entity moved this tick (xo != getX() || zo != getZ() || yo != getY()) and then, moved or
 * not, sends ClientBoundEntityHitSwayingBlockMessage(pos, entity id) through Moonlight's
 * NetworkHelper.sendToAllClientPlayersTrackingEntity (NeoForge PacketDistributor.sendToPlayersTrackingEntity) to every
 * player tracking the entity: 20 messages a second per entity standing in such a cell (mobs in a pen lit by wall lanterns,
 * villagers at a lantern-lit wall), each encoded and sent once per tracking player. On the client the message makes the
 * lantern's swing animation react to that entity's client-side motion (PendulumAnimation: velocity of at least 0.01,
 * SwayingAnimation: more than 0.05).
 *
 * What the switch changes. The message is sent only on ticks where the entity's position changed (the same test the game
 * event above uses); an entity that stands still in the cell sends nothing. The game event, the local player's own
 * collisions (the client branch of the same method) and every moving entity's messages are unchanged.
 * Trade-off: a lantern may stay still where the stock code would make it twitch, when an entity that did not move on the
 * server this tick still has motion on the client (interpolation, knockback in flight, a motion packet not yet superseded).
 *
 * -Dbons_and_furious.amendmentsIdleSwayPacketSkip=false sends every message (emergency off; the config key decides whether
 * the mixin is applied at all).
 * -Dbons_and_furious.amendmentsIdleSwayPacketSkip.shadow=true (verification runs only): every message is sent;
 * SHADOW_CHECKS counts the sends, SHADOW_MISMATCHES the ones the switch would have left out. SKIPPED counts the messages
 * left out in normal mode.
 */
public final class AmendmentsSway {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.amendmentsIdleSwayPacketSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.amendmentsIdleSwayPacketSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SKIPPED = new AtomicLong();
    private static volatile boolean announced;

    private AmendmentsSway() {
    }

    /** WallLanternBlock.entityInside, server branch: NetworkHelper.sendToAllClientPlayersTrackingEntity(entity, message). */
    public static void send(Entity entity, CustomPacketPayload message, Operation<Void> original) {
        if (!enabled || entity == null || moved(entity)) {
            original.call(entity, message);
            return;
        }
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            SHADOW_MISMATCHES.incrementAndGet();
            original.call(entity, message);
            return;
        }
        SKIPPED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: amendments_idle_sway_packet_skip applies (wall lanterns no longer send a sway message for entities that did not move)");
        }
    }

    /** The test entityInside's game-event branch makes: the position changed this tick (NaN counts as changed, as there). */
    static boolean moved(Entity e) {
        return e.xo != e.getX() || e.zo != e.getZ() || e.yo != e.getY();
    }
}
