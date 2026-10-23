package bons.furious.patch.stale_player_maps;

import bons.furious.patch.stale_player_maps.StalePlayerKeys.Kind;
import bons.furious.patch.stale_player_maps.StalePlayerKeys.Side;
import java.util.Map;

/**
 * Bons and Furious fix stale_player_map_purge_treechop (TreeChop 0.19.0 (mods.toml: 0.18.8), MIT; server). Ours; no TreeChop
 * code is carried.
 *
 * NoChopRecursion (active with preventChopRecursion = true) keeps the last chop tick per player in a static HashMap that only
 * grows: every chopping ServerPlayer of every login stays alive (StartChopEvent is constructed only with a ServerPlayer, in
 * ForgePlatform.startChopEvent). The map is handed to {@link StalePlayerKeys} as a server ID map: a logged-out player's entry
 * goes, a respawned player takes over its predecessor's key (same id, same value). onChop's getOrDefault/put answers stay
 * the same for every player that can still chop.
 *
 * Runtime flag: -Dbons_and_furious.stalePlayerMapPurgeTreechop=false leaves the map alone.
 */
public final class TreeChopMaps {
    public static final String KEY = "stale_player_map_purge_treechop";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeTreechop", "true"));

    private TreeChopMaps() {
    }

    /** From NoChopRecursion.<clinit> (our mixin). */
    public static void track(Map<?, ?> lastChopTickByPlayers) {
        StalePlayerKeys.track(KEY, "TreeChop NoChopRecursion.lastChopTickByPlayers", lastChopTickByPlayers, Kind.ID, Side.SERVER, null, () -> enabled);
    }
}
