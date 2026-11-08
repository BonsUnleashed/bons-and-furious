package bons.furious.patch.skyarena_coo49;

import bons.furious.patch.stale_player_maps.StalePlayerKeys;
import bons.furious.patch.stale_player_maps.StalePlayerKeys.Kind;
import bons.furious.patch.stale_player_maps.StalePlayerKeys.Side;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix stale_player_map_purge_skyarena (Sky Arena 1.3.6-1.21.1, MIT; server). Ours; no Sky Arena code is
 * carried. Idea: Collections Of Optimizations (skyarena.pruneAltarPlayerMaps), idea text only.
 *
 * Sky Arena's AltarBlockEntity keeps two static HashMaps keyed by Player objects: activeAltarBlocks (which altar a player
 * lit: recordAltarActivation puts, getAltarPosForPlayer gets, removeAltarActivationForPlayer removes the altar's own
 * activating player when a fight ends) and playerMessageTimestamps (when a player was last told the difficulty:
 * putPlayerMessageTimestamps and updateDifficultyMessages put, updateDifficultyMessages reads with getOrDefault; nothing
 * ever removes). A respawn keeps the OLD player object as the key (HashMap keeps the key on put with an equal key; the
 * successor gets the same entity id in PlayerList.respawn) and a logout leaves the entry, so every respawn and logout of
 * an altar user keeps a dead ServerPlayer (inventory, connection, level) in memory until a restart. Both maps are only
 * used on the server (the altar's ticker exists only on server levels; AltarBlock.use writes on the server).
 *
 * What the fix does: both maps go to the shared layer of the stale_player_map_purge_* fixes (StalePlayerKeys in the
 * stale_player_maps group) as server ID maps: when a player object joins a level the equal key (same entity id: a
 * respawned player) is swapped to the new object with the same value, and a player's entry goes when it leaves its level
 * with UNLOADED_WITH_PLAYER (logout, server stop). That purge comes after Sky Arena's own logout handler: PlayerList.remove
 * posts PlayerLoggedOutEvent first and removes the player from its level (EntityLeaveLevelEvent) afterwards, so
 * PlayerLogoutHandler still finds the entry.
 *
 * Why every answer stays the same: HashMap compares Player keys with Entity.equals / hashCode (the entity id). A swap
 * keeps the key's id and value; a purged id is never handed out again on that server. The altar's own activatingPlayer
 * field (the identity test in the logout and death handlers) is not touched. No reader iterates either map.
 *
 * -Dbons_and_furious.stalePlayerMapPurgeSkyarena=false leaves the maps alone. If the shared layer cannot take the maps, one
 * WARN and the maps stay as Sky Arena keeps them (this runs inside AltarBlockEntity's static initializer and never throws).
 */
public final class SkyarenaAltarMaps {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "stale_player_map_purge_skyarena";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeSkyarena", "true"));
    /** For the harness: how many maps reached the shared layer. */
    public static volatile int tracked;

    private SkyarenaAltarMaps() {
    }

    /** From AltarBlockEntity.<clinit> RETURN (our mixin): hand both maps over. */
    public static void track(Map<?, ?> activeAltarBlocks, Map<?, ?> playerMessageTimestamps) {
        try {
            StalePlayerKeys.track(KEY, "Sky Arena AltarBlockEntity.activeAltarBlocks", activeAltarBlocks, Kind.ID, Side.SERVER, null, () -> enabled);
            tracked++;
            StalePlayerKeys.track(KEY, "Sky Arena AltarBlockEntity.playerMessageTimestamps", playerMessageTimestamps, Kind.ID, Side.SERVER, null, () -> enabled);
            tracked++;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} cannot reach the shared player-map layer ({}); Sky Arena's altar player maps stay as the mod keeps them", KEY, t.toString());
        }
    }
}
