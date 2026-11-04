package bons.furious.patch.chunksending_solo;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch chunksending_solo_no_packet_cache (Chunk Sending 3.7, All Rights Reserved: none of its code is
 * carried; server side incl. the integrated server; since 1.0.49). Trade-off (off by default). SRG member names.
 * Idea: Collections Of Optimizations (chunksending.cacheOnlyWhenShared), idea text only.
 *
 * What Chunk Sending does. Its ChunkMapPacketCacheMixin adds two handlers to ChunkMap.playerLoadedChunk (m_183760_):
 * chunksending$prioCached (HEAD) hands the chunk's cached ClientboundLevelChunkWithLightPacket to the player when the packet
 * holder is still empty, and chunksending$cachePacket (after the packet is built) stores the built packet on the chunk with a
 * deadline of five minutes (EventHandler.addToClear). Both act only on the server thread for Chunk Sending's players. The cache
 * is cleared on every block, block entity, light, heightmap and biome change of the chunk and when the chunk unloads, so a
 * served copy equals a fresh build; its use is the build it saves when a chunk is sent again (to another player, or to the
 * same player after it left the chunk's range), its cost the packets it holds: a few dozen kilobytes each (block sections plus
 * copies of the light layers), for every chunk sent in the last five minutes that is still loaded.
 *
 * What the switch changes. While the server has fewer than two players online, both handlers return at once: no packet is
 * served from the cache and none is stored, so every chunk packet is built as without Chunk Sending's cache. With two or
 * more players the handlers run unchanged. Packets stored before the second player left are no longer served while one
 * player is online and age out through Chunk Sending's own five-minute expiry (Bons and Furious' chunksending_expiry_queue
 * keeps that cheap).
 * Trade-off: less memory held while you play alone; a chunk you walk back into within five minutes is built again (the CPU
 * time of one packet build per such chunk) instead of reusing its stored copy. Nothing else: the packets the player gets
 * have the same content either way (the cache is coherent), no saved data, no packet order, no client/server difference.
 *
 * -Dbons_and_furious.chunkSendingSoloNoPacketCache=false runs the handlers unchanged (emergency off; the config key decides
 * whether the mixin is applied at all).
 * -Dbons_and_furious.chunkSendingSoloNoPacketCache.shadow=true (verification runs only): the handlers run unchanged and
 * SHADOW_CHECKS counts the handler calls, SHADOW_MISMATCHES the calls the switch would have left out (serves + stores while
 * alone), so a rig can measure how often the cache is used by a single player; SKIPPED_SERVES / SKIPPED_STORES count the
 * calls left out in normal mode.
 */
public final class ChunkSendingSolo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.chunkSendingSoloNoPacketCache", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.chunkSendingSoloNoPacketCache.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    public static final AtomicLong SKIPPED_SERVES = new AtomicLong(), SKIPPED_STORES = new AtomicLong();
    private static volatile boolean announced;

    private ChunkSendingSolo() {
    }

    /** At the start of chunksending$prioCached: true = return at once (serve nothing from the cache). */
    public static boolean skipServe(ServerLevel level) {
        if (!enabled) return false;
        boolean solo = solo(level);
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (solo) SHADOW_MISMATCHES.incrementAndGet();
            return false;
        }
        if (solo) {
            SKIPPED_SERVES.incrementAndGet();
            announce();
        }
        return solo;
    }

    /** At the start of chunksending$cachePacket: true = return at once (store nothing). */
    public static boolean skipStore(ServerLevel level) {
        if (!enabled) return false;
        boolean solo = solo(level);
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (solo) SHADOW_MISMATCHES.incrementAndGet();
            return false;
        }
        if (solo) {
            SKIPPED_STORES.incrementAndGet();
            announce();
        }
        return solo;
    }

    /** Fewer than two players online on the level's server (PlayerList.getPlayerCount = its players list's size). */
    static boolean solo(ServerLevel level) {
        MinecraftServer server = level == null ? null : level.m_7654_();
        PlayerList players = server == null ? null : server.m_6846_();
        return players != null && players.m_11309_() < 2;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: chunksending_solo_no_packet_cache applies (with one player online Chunk Sending keeps no copy of the chunk packets it sends)");
        }
    }
}
