package bons.furious.patch.legendarymonsters_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch legendary_monsters_boss_music_trackers (Legendary Monsters 2.2.3 for Minecraft 1.21.1; server;
 * since 1.0.49). Exact variant. Idea: Collections Of Optimizations (legendarymonsters.bossMusicInterval), idea text only. Our
 * own logic; no Legendary Monsters code (All Rights Reserved).
 *
 * IAnimatedBoss.tick sends a PlayBossMusicMessage (play or stop) every server tick through LegendaryMonsters.sendMSGToAll,
 * which is PacketDistributor.sendToAllPlayers: one ClientboundCustomPayloadPacket of the payload to EVERY online player of
 * the server (all dimensions). The client's handler (LMServerPayLoadHandler.handleMusic) acts only when its level holds an
 * IAnimatedBoss under the message's entity id.
 *
 * What changes: the same payload goes, as the same packet (PacketDistributor.sendToPlayersTrackingEntity: a
 * ClientboundCustomPayloadPacket through ServerChunkCache.broadcast), to the players that track the boss (the boss's
 * ChunkMap.TrackedEntity.seenBy), each getting exactly the one packet stock sends it. The one difference (exact variant):
 * no packet to a player whose client does not hold the boss. Such a client cannot act on it: it holds an entity only while
 * the server has it in seenBy (the spawn and remove packets go out when ChunkMap's tracker adds or removes the player, on
 * the same connection, in order), and entity ids are unique on a server, so its level answers null for the id and the
 * handler does nothing. A boss with no tracker entry (an entity type with tracking range 0, never sent to any client) gets
 * no packet at all, where stock's packets reach no client that holds it.
 *
 * -Dbons_and_furious.legendaryMonstersBossMusicTrackers=false sends as stock.
 */
public final class BossMusicTrackers {
    public static final String KEY = "legendary_monsters_boss_music_trackers";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.legendaryMonstersBossMusicTrackers", "true"));
    private static volatile boolean announced;

    private BossMusicTrackers() {
    }

    /** The wrapped LegendaryMonsters.sendMSGToAll(message) of a boss's tick. */
    public static void send(Entity boss, CustomPacketPayload message, Operation<Void> original) {
        if (!enabled || !(boss.level() instanceof ServerLevel)) {
            original.call(message);
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntity(boss, message);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Legendary Monsters' boss music messages go to the players who track the boss, not to every online player)", KEY);
        }
    }
}
