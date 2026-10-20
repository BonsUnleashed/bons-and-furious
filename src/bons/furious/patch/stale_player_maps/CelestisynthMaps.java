package bons.furious.patch.stale_player_maps;

import bons.furious.patch.stale_player_maps.StalePlayerKeys.Kind;
import bons.furious.patch.stale_player_maps.StalePlayerKeys.Side;
import java.util.Map;

/**
 * Bons and Furious fix stale_player_map_purge_celestisynth (Celestisynth 1.3.4, MIT; client). Ours; no Celestisynth code is
 * carried. Not installed in our pack: offline proof only.
 *
 * CSAnimator registers three animation layers for every AbstractClientPlayer that PlayerAnimator constructs and keeps them in
 * three static IdentityHashMaps that are never emptied, so every client player object of the session stays alive (with its
 * ClientLevel) and AnimationManager.playAnimation's containsValue scans grow with it. Celestisynth's readers only ask for
 * players of the current level (UpdateAnimationToAllPacket looks them up by entity id) and for the layers of such players,
 * so the entry of a player object that left its level, whose level was replaced, or whose session ended is never read again;
 * {@link StalePlayerKeys} removes exactly those (identity maps: T5, T6, T3). Note for other mods: the three maps are public;
 * a mod iterating them would no longer see departed players.
 *
 * Runtime flag: -Dbons_and_furious.stalePlayerMapPurgeCelestisynth=false leaves the maps alone.
 */
public final class CelestisynthMaps {
    public static final String KEY = "stale_player_map_purge_celestisynth";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeCelestisynth", "true"));

    private CelestisynthMaps() {
    }

    /** From CSAnimator.<clinit> (our mixin). */
    public static void track(Map<?, ?> animationData, Map<?, ?> otherAnimationData, Map<?, ?> mirroredAnimationData) {
        StalePlayerKeys.track(KEY, "Celestisynth CSAnimator.animationData", animationData, Kind.IDENTITY, Side.CLIENT, null, () -> enabled);
        StalePlayerKeys.track(KEY, "Celestisynth CSAnimator.otherAnimationData", otherAnimationData, Kind.IDENTITY, Side.CLIENT, null, () -> enabled);
        StalePlayerKeys.track(KEY, "Celestisynth CSAnimator.mirroredAnimationData", mirroredAnimationData, Kind.IDENTITY, Side.CLIENT, null, () -> enabled);
    }
}
