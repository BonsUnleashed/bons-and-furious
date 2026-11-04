package bons.furious.patch.photon_c49;

import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch photon_block_effect_cache_prune (Photon 1.1.17; client). Ours; no Photon code is carried.
 *
 * What Photon does. BlockEffect.CACHE maps every block position that ever showed a block effect to a list of its effects.
 * When an effect ends because its block changed or unloaded, BlockEffect.updateFXObjectTick destroys the effect's runtime
 * and removes the effect from its position's list with CACHE.computeIfAbsent(pos, new list).remove(effect): the entry stays
 * behind with an empty list. Nothing removes empty entries until the client changes level (ParticleEngine.setLevel clears
 * the map), so the map keeps about 140 bytes for every position whose effect ended, for as long as the player stays in
 * that dimension.
 *
 * What the switch does. Only when BlockEffect.start adds a NEW entry (its computeIfAbsent had to create the position's
 * list: a flag set by that list's constructor in Photon's own mapping function) and the map has reached its mark, every
 * other entry whose list is empty is removed; the mark is then twice the entries kept (at least 64). So the map never grows
 * past twice the positions with effects (plus 64) through start, ended effects and restarts at a known position cost
 * nothing extra (a restart finds its entry and reads one flag), and the cleanup is one pass over the map per doubling.
 * Fix: every reader of the map treats a missing entry as an empty list (BlockEffect.start uses computeIfAbsent,
 * RemoveBlockEffectCommand null-checks get, the clears clear), and the sweep never removes the entry the running start
 * just looked up (it adds its effect to that list next) and runs on the client thread inside start, where no other Photon
 * code holds a list of the map; so starts, skips and removals are decided exactly as before, only empty entries are gone.
 *
 * -Dbons_and_furious.photonBlockEffectCachePrune=false switches it off at run time.
 */
public final class PhotonCachePrune {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.photonBlockEffectCachePrune", "true"));
    /** The smallest map size that triggers a sweep. */
    public static final int MIN_SWEEP = 64;
    /** Set when start's mapping function built a new entry (client thread), read right after its computeIfAbsent. */
    public static boolean inserted;
    private static volatile boolean announced;
    /** Map size at which the next sweep runs (client thread). */
    private static int nextSweep = MIN_SWEEP;
    /** Empty entries removed so far and sweeps run (client thread only; for in-game checks). */
    public static long pruned, sweeps;

    private PhotonCachePrune() {}

    /** Called right after BlockEffect.start's computeIfAbsent added a new entry; {@code keep} is that entry's list. */
    public static void afterInsert(Map<?, ?> cache, Object keep) {
        inserted = false;
        if (enabled && cache.size() >= nextSweep) sweep(cache, keep);
    }

    private static void sweep(Map<?, ?> cache, Object keep) {
        int before = cache.size();
        cache.values().removeIf(list -> list != keep && list instanceof List<?> l && l.isEmpty());
        int after = cache.size();
        nextSweep = Math.max(MIN_SWEEP, after * 2);
        sweeps++;
        if (after < before) {
            pruned += before - after;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: photon_block_effect_cache_prune applies (empty Photon block effect cache entries are removed; {} this time)", before - after);
            }
        }
    }
}
