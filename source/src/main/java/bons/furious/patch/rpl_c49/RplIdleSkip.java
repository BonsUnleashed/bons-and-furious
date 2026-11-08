package bons.furious.patch.rpl_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch rpl_idle_chunk_manager_skip (Ritchie's Projectile Library 2.1.2, MIT; server side incl. the
 * integrated server). Ours; no Ritchie's Projectile Library code is carried.
 *
 * What the library does. ChunkManager.tick(ServerLevel) runs for every level on every server tick. Even when the manager
 * holds nothing (no forced chunk, nothing queued, nothing loaded), it asks the level for its forced chunks, reads four
 * config values, allocates a LongOpenHashSet, walks an empty map, trims its four collections and calls setDirty(), so
 * every autosave compresses and writes an empty "LoadedChunks" file for every level.
 *
 * What the switch does. An inject at the head of tick returns at once when chunks, queue, inQueue and loaded are all
 * empty and the manager is settled: a full tick has already started with all four empty and nothing was added since (only
 * tick removes from them, so they stayed empty). A tick that finds anything in them clears "settled" and runs as before;
 * its only extra cost is the emptiness test.
 *
 * Exact variant. With all four empty every statement of tick is a no-op except setDirty(): the loaded-map walk visits
 * nothing, the poll count is min(.., queue.size() = 0) = 0, the expired set stays empty, and the four trims find
 * collections the settling tick already trimmed (that tick started with them empty, nothing it runs adds to them, and it
 * trims at its end; LongArrayFIFOQueue.trim of an empty queue rebuilds the same one-slot array), so their tables, and every
 * later iteration order, are the ones stock has. The forced-chunk set is only read, and the level's forced-chunk data was
 * already read by the settling tick. The one difference: an idle manager is no longer marked dirty every tick, so its
 * (empty) file is not rewritten on every autosave; its loaded state is the same either way. (An idle tick can only throw
 * when the library's server config is not loaded, before its trims; the game stops on that first exception, so no later
 * idle tick is ever skipped after it.)
 *
 * -Dbons_and_furious.rplIdleChunkManagerSkip=false switches it off at run time.
 */
public final class RplIdleSkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.rplIdleChunkManagerSkip", "true"));
    private static volatile boolean announced;

    private RplIdleSkip() {}

    /** Called when an idle manager skips its tick: one INFO line the first time. */
    public static void skipped() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: rpl_idle_chunk_manager_skip applies (idle Ritchie's Projectile Library chunk managers skip their tick)");
        }
    }
}
