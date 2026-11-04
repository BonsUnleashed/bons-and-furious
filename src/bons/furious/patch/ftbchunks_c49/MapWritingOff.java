package bons.furious.patch.ftbchunks_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch ftbchunks_map_writing_off (FTB Chunks 2001.3.8, client; kind change, off by default). Ours; no
 * FTB Chunks code is carried.
 *
 * What FTB Chunks does. Every chunk the client receives (and, every few ticks, every chunk with changed blocks) becomes a
 * ChunkUpdateTask on FTB Chunks' single map thread through FTBChunksClient.queueOrExecute: 256 columns of heightmap, water
 * and height scan, block state, light, biome and colour lookups. Each task that changes the recorded map re-renders the
 * 512 x 512 region image in the background, schedules minimap rebuilds on the render thread and marks the region for the
 * next autosave (1.3 million pixel copies and five PNG encodings per region). FTBChunksClient.rerender collects the
 * changed block positions for those re-render tasks.
 *
 * What the switch does. With the switch on, both methods return at once: FTB Chunks stops recording terrain into its map.
 * Trade-off (off by default): the minimap, the large map and the claim screen keep showing what was recorded before (new
 * areas stay blank, changed blocks keep their old look); claims, force-loads, waypoints and the loading of recorded regions
 * work as before (claim data comes from the server through FTB Chunks' client task queue, not through queueOrExecute).
 * Client only: no packet, no saved data format change (only fewer region writes).
 *
 * -Dbons_and_furious.ftbchunksMapWritingOff=false switches it off at run time.
 */
public final class MapWritingOff {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ftbchunksMapWritingOff", "true"));
    /** Map tasks and block re-render requests dropped (harness and rig counters). */
    public static long droppedTasks, droppedRerenders;
    private static volatile boolean announced;

    private MapWritingOff() {}

    public static boolean dropTask() {
        if (!enabled) return false;
        droppedTasks++;
        announce();
        return true;
    }

    public static boolean dropRerender() {
        if (!enabled) return false;
        droppedRerenders++;
        announce();
        return true;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: ftbchunks_map_writing_off applies (FTB Chunks no longer records terrain into its map)");
        }
    }
}
