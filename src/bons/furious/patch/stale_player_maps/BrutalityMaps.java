package bons.furious.patch.stale_player_maps;

import bons.furious.patch.stale_player_maps.StalePlayerKeys.Kind;
import bons.furious.patch.stale_player_maps.StalePlayerKeys.Side;
import java.util.Map;

/**
 * Bons and Furious fix stale_player_map_purge_brutality (Brutality 0.8.4, MIT; client). Ours; no Brutality code is carried.
 * Not installed in our pack: offline proof only.
 *
 * AnimationHelper keeps one SpeedModifier and one MirrorModifier per player in two static HashMaps (computeIfAbsent only),
 * so every player object it ever animated stays alive. These are ID maps (Entity.equals compares ids): a returning player
 * reuses the modifiers of its predecessor, internal timing state included, so an entry may not go while its id can come
 * back. {@link StalePlayerKeys} lets a joining player take over an equal key (same modifiers) and empties the maps when the
 * client session ends.
 *
 * Runtime flag: -Dbons_and_furious.stalePlayerMapPurgeBrutality=false leaves the maps alone.
 */
public final class BrutalityMaps {
    public static final String KEY = "stale_player_map_purge_brutality";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeBrutality", "true"));

    private BrutalityMaps() {
    }

    /** From AnimationHelper.<clinit> (our mixin). */
    public static void track(Map<?, ?> speedModifierMap, Map<?, ?> mirrorModifierMap) {
        StalePlayerKeys.track(KEY, "Brutality AnimationHelper.speedModifierMap", speedModifierMap, Kind.ID, Side.CLIENT, null, () -> enabled);
        StalePlayerKeys.track(KEY, "Brutality AnimationHelper.mirrorModifierMap", mirrorModifierMap, Kind.ID, Side.CLIENT, null, () -> enabled);
    }
}
