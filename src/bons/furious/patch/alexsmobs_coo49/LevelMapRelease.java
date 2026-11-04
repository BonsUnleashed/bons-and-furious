package bons.furious.patch.alexsmobs_coo49;

import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.level.LevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch alexsmobs_release_level_maps (Alex's Mobs 1.22.9, both sides; the maps fill on the server).
 *
 * Fix: Alex's Mobs keeps two static maps keyed by a level and never removes an entry: AMWorldData.dataMap (Level ->
 * its world data) and ServerEvents.BEACHED_CACHALOT_WHALE_SPAWNER_MAP (ServerLevel -> its whale spawner). Every level
 * that ever got an entry stays reachable for the rest of the JVM's life; in singleplayer that is every world opened
 * and closed in one game session (and through the level, its chunks and entities).
 *
 * The switch hands both maps to this helper once (at the end of each class's static initializer) and registers one
 * listener for Forge's LevelEvent.Unload (fired when a level unloads: server stop, a dimension unloading, leaving a
 * world on the client), which removes that level from both maps on the thread that unloads it (the maps' own thread).
 * A level that is loaded again gets a new entry from Alex's Mobs' own code, as on first use. Nothing else changes; a mod
 * that already clears the maps (AllTheLeaks) only makes the removal find nothing. Runtime flag
 * -Dbons_and_furious.alexsmobsReleaseLevelMaps=false. Alex's Mobs is LGPL-3.0; no code of it is carried.
 */
public final class LevelMapRelease {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.alexsmobsReleaseLevelMaps", "true"));
    private static volatile Map<?, ?> worldData;
    private static volatile Map<?, ?> whaleSpawners;
    private static volatile boolean registered;

    private LevelMapRelease() {
    }

    public static void worldData(Map<?, ?> map) {
        worldData = map;
        register();
    }

    public static void whaleSpawners(Map<?, ?> map) {
        whaleSpawners = map;
        register();
    }

    private static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, LevelMapRelease::onUnload);
        } catch (Throwable t) {   // never break Alex's Mobs' class initialisation: without the listener nothing changes
            LOGGER.warn("Bons and Furious: alexsmobs_release_level_maps could not register its level unload listener ({}); Alex's Mobs' level maps stay as before", t.toString());
        }
    }

    /** LevelEvent.Unload: drop the unloading level from Alex's Mobs' two static maps. */
    public static void onUnload(LevelEvent.Unload event) {
        if (!enabled) return;
        LevelAccessor level = event.getLevel();
        boolean removed = false;
        Map<?, ?> data = worldData;
        if (data != null) removed |= data.remove(level) != null;
        Map<?, ?> whales = whaleSpawners;
        if (whales != null && level instanceof ServerLevel) removed |= whales.remove(level) != null;
        if (removed) LOGGER.debug("Bons and Furious: alexsmobs_release_level_maps: released {}", level);
    }
}
