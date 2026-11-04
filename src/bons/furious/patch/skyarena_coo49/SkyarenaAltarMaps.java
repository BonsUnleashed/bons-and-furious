package bons.furious.patch.skyarena_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix stale_player_map_purge_skyarena (Sky Arena 1.3.6, MIT; server). Ours; no Sky Arena code is carried.
 * Idea: Collections Of Optimizations (skyarena.pruneAltarPlayerMaps), idea text only.
 *
 * Sky Arena's AltarBlockEntity keeps two static HashMaps keyed by Player objects: activeAltarBlocks (which altar a player
 * lit: recordAltarActivation puts, getAltarPosForPlayer gets, removeAltarActivationForPlayer removes the altar's own
 * activating player when a fight ends) and playerMessageTimestamps (when a player was last told the difficulty:
 * putPlayerMessageTimestamps and updateDifficultyMessages put, updateDifficultyMessages reads with getOrDefault; nothing
 * ever removes). A respawn keeps the OLD player object as the key (HashMap keeps the key on put with an equal key; the
 * successor has the same entity id) and a logout leaves the entry, so every respawn and logout of an altar user keeps a
 * dead ServerPlayer (inventory, connection, level) in memory until a restart. Both maps are only used on the server
 * (the altar's ticker exists only on server levels; AltarBlock.use writes on the server).
 *
 * What the fix does: both maps go to the shared layer of the stale_player_map_purge_* fixes (StalePlayerKeys in the
 * stale_player_maps group, reached by name so this group builds on its own) as server ID maps: when a player object
 * joins a level the equal key (same entity id: a respawned player) is swapped to the new object with the same value, and
 * a player's entry goes when it leaves its level with UNLOADED_WITH_PLAYER (logout, server stop). That purge comes after
 * Sky Arena's own logout handler (PlayerList.remove posts PlayerLoggedOutEvent before the player leaves its level), which
 * still finds the entry.
 *
 * Why every answer stays the same: HashMap compares Player keys with Entity.equals / hashCode (the entity id). A swap
 * keeps the key's id and value; a purged id is never handed out again on that server. The altar's own activatingPlayer
 * field (the identity test in the logout handler) is not touched. No reader iterates either map.
 *
 * -Dbons_and_furious.stalePlayerMapPurgeSkyarena=false leaves the maps alone. If the shared layer cannot be reached, one
 * WARN and the maps stay as Sky Arena keeps them.
 */
public final class SkyarenaAltarMaps {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "stale_player_map_purge_skyarena";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeSkyarena", "true"));
    /** For the harness: how many maps reached the shared layer. */
    public static volatile int tracked;

    private SkyarenaAltarMaps() {
    }

    /** From AltarBlockEntity.<clinit> RETURN (our mixin): hand both maps over. Never throws (it runs in a static initializer). */
    public static void track(Map<?, ?> activeAltarBlocks, Map<?, ?> playerMessageTimestamps) {
        try {
            ClassLoader cl = SkyarenaAltarMaps.class.getClassLoader();
            Class<?> layer = Class.forName("bons.furious.patch.stale_player_maps.StalePlayerKeys", true, cl);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> kind = (Class<Enum>) Class.forName("bons.furious.patch.stale_player_maps.StalePlayerKeys$Kind", true, cl);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> side = (Class<Enum>) Class.forName("bons.furious.patch.stale_player_maps.StalePlayerKeys$Side", true, cl);
            MethodHandle track = MethodHandles.publicLookup().findStatic(layer, "track",
                    MethodType.methodType(void.class, String.class, String.class, Map.class, kind, side, Object.class, BooleanSupplier.class));
            @SuppressWarnings("unchecked")
            Object id = Enum.valueOf(kind, "ID");
            @SuppressWarnings("unchecked")
            Object server = Enum.valueOf(side, "SERVER");
            BooleanSupplier on = () -> enabled;
            track.invoke(KEY, "Sky Arena AltarBlockEntity.activeAltarBlocks", activeAltarBlocks, id, server, null, on);
            tracked++;
            track.invoke(KEY, "Sky Arena AltarBlockEntity.playerMessageTimestamps", playerMessageTimestamps, id, server, null, on);
            tracked++;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} cannot reach the shared player-map layer ({}); Sky Arena's altar player maps stay as the mod keeps them", KEY, t.toString());
        }
    }
}
