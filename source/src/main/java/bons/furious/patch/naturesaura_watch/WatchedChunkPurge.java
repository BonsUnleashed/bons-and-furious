package bons.furious.patch.naturesaura_watch;

import com.google.common.collect.SetMultimap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.bus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix naturesaura_watched_chunk_purge (Nature's Aura 39.4, MIT; server). Exact variant. Ours; no Nature's
 * Aura code is carried. Idea: Collections Of Optimizations (naturesaura.resolveWatchedChunks), idea text only; keeping the
 * stock send timing is ours. Not installed in our pack: offline proof only.
 *
 * Nature's Aura sends a chunk's aura data to a player when the chunk is block-ticking: onChunkWatch appends (player UUID,
 * chunk) to the static multimap PENDING_AURA_CHUNKS, and every 10 ticks onPlayerTick walks the player's list and sends and
 * drops each entry whose chunk is ticking in the player's CURRENT level. Nothing removes an entry when the chunk stops being
 * watched (moving away, a dimension change, logout, respawn), and the list is keyed by UUID, so it survives relogs: with a
 * view distance above the simulation distance every chunk flown past that never got to tick stays in the list for the rest
 * of the server's run, and every 10 ticks each of them costs a chunk-ticking lookup.
 *
 * The fix: when Forge reports that a player stops watching a chunk (ChunkWatchEvent.UnWatch, fired by
 * ChunkMap.updateChunkTracking whenever tracking ends - also on logout, respawn and dimension change), one entry for that
 * (player, chunk) is removed, if there is one. Watch (fired when the chunk is sent) and UnWatch alternate per player and chunk,
 * so the list holds exactly the watched chunks that have not ticked yet. Server state is identical (Nature's Aura keeps no
 * other state about it); the only difference is packets: a stale entry no longer sends a chunk's aura data later (a duplicate
 * of the regular send when the chunk is watched again, or a send for a chunk the client does not have, which the client keeps
 * pending until it loads that chunk or disconnects). The regular send still comes when the watched chunk ticks, so the
 * client's aura data ends up the same.
 *
 * Runtime flag: -Dbons_and_furious.naturesauraWatchedChunkPurge=false keeps every entry as before.
 */
public final class WatchedChunkPurge {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.naturesauraWatchedChunkPurge", "true"));
    public static final AtomicLong REMOVED = new AtomicLong();
    private static volatile SetMultimap<UUID, ChunkPos> pending;
    private static volatile boolean registered, announced;

    private WatchedChunkPurge() {
    }

    /**
     * From CommonEvents.<clinit> (our mixin): Nature's Aura's multimap, and the UnWatch listener once. Runs inside Nature's
     * Aura's static initializer, so a failure here must never fail that class (the list then stays as stock).
     */
    public static synchronized void track(SetMultimap<UUID, ChunkPos> map) {
        pending = map;
        if (registered) return;
        registered = true;
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ChunkWatchEvent.UnWatch.class, WatchedChunkPurge::onUnWatch);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: naturesaura_watched_chunk_purge could not register its listener ({}); the pending list stays as stock", t.toString());
        }
    }

    public static void onUnWatch(ChunkWatchEvent.UnWatch event) {
        SetMultimap<UUID, ChunkPos> map = pending;
        if (!enabled || map == null) return;
        if (map.remove(event.getPlayer().getUUID(), event.getPos())) {
            REMOVED.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: naturesaura_watched_chunk_purge applies (chunks a player stops watching leave Nature's Aura's pending list)");
            }
        }
    }
}
