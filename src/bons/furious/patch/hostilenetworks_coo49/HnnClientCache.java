package bons.furious.patch.hostilenetworks_coo49;

import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch hostilenetworks_client_entity_cache_lean (since 1.0.49; Hostile Neural Networks 5.3.3; client).
 * Helper of HnnClientEntityCacheMixin; no Hostile Neural Networks code is carried.
 *
 * What Hostile Neural Networks does. ClientEntityCache keeps one display entity per (client level, entity type) in two
 * IdentityHashMaps. computeIfAbsent(type, level, nbt), asked for every data model drawn (inventory slots, the deep learner
 * screen, recipe viewers, every frame), is CACHE.computeIfAbsent(level, ...) then inner.computeIfAbsent(type, lambda
 * capturing type, level and nbt): a new lambda object on every call although the entity is almost always there. Its
 * ClientTickEvent listener runs CACHE.values().stream().map(Map::values).flatMap(Collection::stream).forEach(e ->
 * e.tickCount++) at the start of every client tick, a four-stage stream even when the cache is empty.
 *
 * What the switch does. A lookup whose level and type are both present returns the cached entity from two plain get calls:
 * Map.computeIfAbsent returns an existing non-null value without calling the function or changing the map, and the cache
 * never holds null (computeIfAbsent stores no null). Any other lookup runs the original method. The tick walks the same
 * maps in the same order with two loops and adds one to each entity's tickCount, as the stream does; with an empty cache
 * there is nothing to walk. The END phase runs the original (which does nothing). Identical results, no lambda or stream
 * objects. -Dbons_and_furious.hostilenetworksClientEntityCacheLean=false runs the originals.
 */
public final class HnnClientCache {
    /** hostilenetworks_client_entity_cache_lean: -Dbons_and_furious.hostilenetworksClientEntityCacheLean=false runs the originals. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.hostilenetworksClientEntityCacheLean", "true"));
    private static volatile boolean announced;

    private HnnClientCache() {
    }

    /** The cached entity for (level, type), or null when the original must run (a level or type not cached yet). */
    public static LivingEntity hit(Map<?, ? extends Map<?, ? extends LivingEntity>> cache, Object type, Object level) {
        Map<?, ? extends LivingEntity> inner = cache.get(level);
        if (inner == null) return null;
        LivingEntity e = inner.get(type);
        if (e != null && !announced) announce();
        return e;
    }

    /** The START-phase tick: tickCount + 1 for every cached entity, levels and types in the maps' iteration order. */
    public static void tick(Map<?, ? extends Map<?, ? extends LivingEntity>> cache) {
        if (cache.isEmpty()) return;
        for (Map<?, ? extends LivingEntity> inner : cache.values()) {
            for (LivingEntity e : inner.values()) e.f_19797_++;
        }
    }

    private static void announce() {
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: hostilenetworks_client_entity_cache_lean applies (data model display entities are looked up without a new lambda per draw)");
    }
}
