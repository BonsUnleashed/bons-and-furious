package bons.furious.patch.stale_player_maps;

import bons.furious.patch.stale_player_maps.StalePlayerKeys.Kind;
import bons.furious.patch.stale_player_maps.StalePlayerKeys.Side;
import java.util.Map;

/**
 * Bons and Furious fix stale_player_map_purge_moreartifacts (More Artifacts 1.5.5, All Rights Reserved: no code of it is
 * carried; both sides). Not installed in our pack: offline proof only.
 *
 * Nine static HashMaps keyed by Player are filled and never emptied (DamageEvents: damage reduction, fire reduction, damage
 * increase, equipped-artifact state, last heal time; MAUtils and the Necroplasm and Spectre amulets: cooldowns; Teleport:
 * teleport state). All are ID maps (Entity.equals compares ids). The heal-time and amulet maps are filled only from
 * LivingHurtEvent handlers (server only); the others from PlayerTickEvent and item use on both sides, with no lock of their
 * own, so {@link StalePlayerKeys} changes those only while one thread can touch them (a dedicated server, or a client without
 * an integrated server) and otherwise leaves the purge to the end of the client session.
 *
 * Runtime flag: -Dbons_and_furious.stalePlayerMapPurgeMoreartifacts=false leaves the maps alone.
 */
public final class MoreArtifactsMaps {
    public static final String KEY = "stale_player_map_purge_moreartifacts";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeMoreartifacts", "true"));

    private MoreArtifactsMaps() {
    }

    public static void trackBoth(String name, Map<?, ?> map) {
        StalePlayerKeys.track(KEY, "More Artifacts " + name, map, Kind.ID, Side.BOTH, null, () -> enabled);
    }

    public static void trackServer(String name, Map<?, ?> map) {
        StalePlayerKeys.track(KEY, "More Artifacts " + name, map, Kind.ID, Side.SERVER, null, () -> enabled);
    }
}
