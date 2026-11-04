package bons.furious.patch.legendarymonsters_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.miauczel.legendary_monsters.LegendaryMonsters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch legendary_monsters_boss_music_trackers (Legendary Monsters 2.2.2, 2.2.4 identical; server; since
 * 1.0.49). Exact variant. Idea: Collections Of Optimizations (legendarymonsters.bossMusicInterval), idea text only. Our own
 * logic; no Legendary Monsters code (All Rights Reserved).
 *
 * IAnimatedBoss.tick sends a PlayBossMusicMessage (play or stop) every server tick through
 * LegendaryMonsters.sendMSGToAll, which encodes and sends one custom payload to EVERY online player of the server (all
 * dimensions). The client's handler acts only when its level holds an IAnimatedBoss under the message's entity id.
 *
 * What changes: the same message, encoded once by Legendary Monsters' own channel (SimpleChannel.toVanillaPacket, the
 * packet sendTo builds), goes to the players that track the boss (ServerChunkCache.broadcast: the boss's
 * ChunkMap.TrackedEntity.seenBy), each getting exactly the one packet stock sends it. The one difference (exact variant):
 * no packet to a player whose client does not hold the boss. Such a client cannot act on it: it holds an entity only
 * while the server has it in seenBy (the spawn and remove packets come from ChunkMap's tracker update, which runs after
 * the entity ticks of the same server tick, on the same connection, in order), and entity ids are unique on a server, so
 * its level answers null for the id and the handler returns. A boss with no tracker entry (an entity type with tracking
 * range 0, never sent to any client) gets no packet at all, where stock's packets reach no client that holds it.
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
    public static void send(Entity boss, Object message, Operation<Void> original) {
        if (!enabled || !(boss.m_9236_() instanceof ServerLevel level)) {
            original.call(message);
            return;
        }
        level.m_7726_().m_8445_(boss, LegendaryMonsters.NETWORKWRAPPER.toVanillaPacket(message, NetworkDirection.PLAY_TO_CLIENT));
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Legendary Monsters' boss music messages go to the players who track the boss, not to every online player)", KEY);
        }
    }
}
