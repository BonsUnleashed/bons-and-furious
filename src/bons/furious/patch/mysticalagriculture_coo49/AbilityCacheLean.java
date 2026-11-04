package bons.furious.patch.mysticalagriculture_coo49;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious 1.0.49 switch mysticalagriculture_ability_cache_lean (Mystical Agriculture 7.0.24, both sides; kind
 * opt, default on; helper of AbilityCacheLeanMixin; no Mystical Agriculture code carried).
 *
 * What Mystical Agriculture does. AbilityCache.getCachedAbilities(player), asked for every player on every tick on both
 * sides (AugmentHandler) and by Health Boost every player tick, builds the player key and runs a stream over the cache's
 * entries: filter (the entry's player map holds the key), map to the augment id, collect into Collectors.toSet(), a new
 * HashSet.
 *
 * What the switch does. The key is built by the mod's own getPlayerKey (the same calls, so the same exceptions), then one
 * plain loop over the same entry set in the same order adds the same ids to a new HashSet: equal contents in the same
 * iteration order (same insertions into the same kind of set), and every caller still gets its own mutable set. Runtime
 * flag bons_and_furious.mysticalagricultureAbilityCacheLean.
 */
public final class AbilityCacheLean {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mysticalagricultureAbilityCacheLean", "true"));
    private static volatile boolean announced;

    private AbilityCacheLean() {
    }

    /** getCachedAbilities' answer for the player whose key is {@code key}. */
    public static Set<String> abilities(Map<String, Map<String, Runnable>> cache, String key) {
        if (!announced) announce();
        HashSet<String> out = new HashSet<>();
        for (Map.Entry<String, Map<String, Runnable>> e : cache.entrySet()) {
            if (e.getValue().containsKey(key)) out.add(e.getKey());
        }
        return out;
    }

    private static void announce() {
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: mysticalagriculture_ability_cache_lean applies (a player's cached augment abilities are gathered with one loop instead of a stream)");
    }
}
