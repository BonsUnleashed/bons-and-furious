package bons.furious.patch.hostilenetworks_coo49;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch hostilenetworks_client_entity_cache_lean (since 1.0.49; Hostile Neural Networks, MIT; 1.21.1
 * tested build 1.21.1-6.5.1; client). Helper of HnnClientEntityCacheMixin; no Hostile Neural Networks code is carried.
 *
 * What Hostile Neural Networks does. ClientEntityCache keeps one display entity per (client level, display entry) in an
 * IdentityHashMap of HashMaps (the display entry is the DisplayEntity record: entity type, display NBT, scale and offsets).
 * computeIfAbsent(display, level), asked for every data model drawn (inventory slots, the deep learner screen, recipe
 * viewers, every frame), is CACHE.computeIfAbsent(level, ...) then inner.computeIfAbsent(display, lambda capturing level and
 * display): a new lambda object on every call although the entity is almost always there. Its ClientTickEvent.Pre listener
 * runs CACHE.values().stream().map(Map::values).flatMap(Collection::stream).forEach(e -> e.tickCount++) at the start of
 * every client tick, a four-stage stream even when the cache is empty.
 *
 * What the switch does. A lookup whose level and display entry are both present returns the cached entity from two plain get
 * calls: Map.computeIfAbsent returns an existing non-null value without calling the function or changing the map (the
 * IdentityHashMap uses Map's default, which is get first; HashMap finds the same node by the same hash and equals), and the
 * cache never holds null (computeIfAbsent stores no null). Any other lookup runs the original method. The tick walks the
 * same maps in the same order with two loops and adds one to each entity's tickCount, as the stream does; with an empty
 * cache there is nothing to walk. Identical results, no lambda or stream objects.
 * -Dbons_and_furious.hostilenetworksClientEntityCacheLean=false runs the originals.
 */
public final class HnnClientCache {
    /** hostilenetworks_client_entity_cache_lean: -Dbons_and_furious.hostilenetworksClientEntityCacheLean=false runs the originals. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.hostilenetworksClientEntityCacheLean", "true"));
    private static volatile boolean announced;

    private HnnClientCache() {
    }

    /** The cached entity for (level, display), or null when the original must run (a level or display entry not cached yet). */
    public static Entity hit(Map<?, ? extends Map<?, ? extends Entity>> cache, Object display, Object level) {
        Map<?, ? extends Entity> inner = cache.get(level);
        if (inner == null) return null;
        Entity e = inner.get(display);
        if (e != null && !announced) announce();
        return e;
    }

    /** The ClientTickEvent.Pre tick: tickCount + 1 for every cached entity, levels and entries in the maps' iteration order. */
    public static void tick(Map<?, ? extends Map<?, ? extends Entity>> cache) {
        if (cache.isEmpty()) return;
        for (Map<?, ? extends Entity> inner : cache.values()) {
            for (Entity e : inner.values()) e.tickCount++;
        }
    }

    private static void announce() {
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: hostilenetworks_client_entity_cache_lean applies (data model display entities are looked up without a new lambda per draw)");
    }
}
