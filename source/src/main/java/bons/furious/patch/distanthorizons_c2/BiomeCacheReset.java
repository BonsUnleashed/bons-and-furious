package bons.furious.patch.distanthorizons_c2;

import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper_neoforge;
import java.lang.reflect.Field;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix distanthorizons_world_change_biome_reset (Distant Horizons, LGPL-3.0; 1.21.1 tested build
 * DistantHorizons-3.3.3-1.21.1-fabric-neoforge; client and integrated server, also a dedicated server's own unload). No
 * Distant Horizons code is carried.
 *
 * Distant Horizons keeps five static biome caches that nothing ever clears:
 *   BiomeWrapper_neoforge.WRAPPER_BY_BIOME            Holder<Biome> -> wrapper (the wrapper keeps the holder)
 *   BiomeWrapper_neoforge.WRAPPER_BY_RESOURCE_LOCATION "minecraft:plains" -> the wrapper deserialize() made first
 *   BlockBiomeWrapperPair.CACHED_PAIR_BY_BIOME_BY_BLOCK  block wrapper -> biome wrapper -> pair (keyed by wrapper equality,
 *                                                   i.e. by the biome's name, so a new world's plains finds the old pair)
 *   AbstractDhTintGetter_neoforge.BIOME_BY_RESOURCE_STRING  name -> the CLIENT level's Holder<Biome> (client class)
 *   AbstractDhTintGetter_neoforge.COLOR_BY_BLOCK_BIOME_PAIR  pair -> tint colour (client class; DH clears it only on resource
 *                                                   reload)
 * After leaving a world (or a server), the next world's LOD tints are therefore resolved against the previous world's
 * Biome objects (grass, foliage and water colours, coldness: wrong as soon as the next world or server defines a biome
 * differently), and the previous world's biome registry, with everything its biomes reference, stays reachable for the
 * rest of the session (a leak per world left).
 *
 * The fix empties the five maps where Distant Horizons already resets its other world state: in SharedApi.setDhWorld,
 * right after its own BlockTextureRegistry.clear() on world unload (before its System.gc()), and again on world load right
 * before the new world's thread pools start (dropping anything a still-running task of the old world put back). Everything
 * is rebuilt lazily from the current world's registries, which is exactly the state of a fresh launch; within one world
 * nothing changes. Nothing that cannot be rebuilt lives in these maps: AbstractDhTintGetter_neoforge.setStaticColor, the
 * only writer of a fixed colour, has no caller in the jar (byte scan). The two client-only maps are only touched on the
 * client (on a dedicated server Distant Horizons never loads that class). Logging: a client biome name that cannot be
 * resolved gets its "using fallback" warning again once in the new world (it is a new lookup there); the warn-once flags
 * are left alone.
 *
 * Runtime flag: -Dbons_and_furious.distanthorizonsWorldChangeBiomeReset=false leaves the maps alone. A map that cannot be
 * reached (another DH build) is skipped with one WARN; the guard normally prevents that.
 *
 * Ported to 1.21.1: DH 3.3.3 keeps the same five maps under the same names (WRAPPER_BY_BIOME is now declared as a
 * ConcurrentMap), filled by the same guarded methods, and setDhWorld resets its other world state at the same places.
 */
public final class BiomeCacheReset {
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.distanthorizonsWorldChangeBiomeReset", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String PAIR = "com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair";
    private static final String TINT = "com.seibel.distanthorizons.common.wrappers.block.AbstractDhTintGetter_neoforge";
    private static volatile boolean announced, warned;
    /** Resets done and entries removed (for probes and the harness). */
    public static volatile long resets, removed;

    private BiomeCacheReset() {
    }

    /** Called from SharedApi.setDhWorld at world unload ("unload") and before a new world's thread pools ("load"). */
    public static void reset(String when) {
        if (!enabled) return;
        long n = 0;
        n += clear(BiomeWrapper_neoforge.WRAPPER_BY_BIOME);
        n += clear(BiomeWrapper_neoforge.WRAPPER_BY_RESOURCE_LOCATION);
        n += clear(staticMap(PAIR, "CACHED_PAIR_BY_BIOME_BY_BLOCK", false));
        if (clientSide()) {
            n += clear(staticMap(TINT, "BIOME_BY_RESOURCE_STRING", true));
            n += clear(staticMap(TINT, "COLOR_BY_BLOCK_BIOME_PAIR", true));
        }
        resets++;
        removed += n;
        if (!announced && n > 0) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_world_change_biome_reset: Distant Horizons' biome caches emptied at world {} ({} entries)", when, n);
        } else {
            LOGGER.debug("Bons and Furious: distanthorizons_world_change_biome_reset: {} biome cache entries removed at world {}", n, when);
        }
    }

    private static long clear(Map<?, ?> map) {
        if (map == null) return 0;
        long n = map.size();
        map.clear();
        return n;
    }

    /** Distant Horizons' private static map, or null (with one WARN) when it cannot be read. */
    private static Map<?, ?> staticMap(String owner, String field, boolean clientClass) {
        try {
            Class<?> c = Class.forName(owner, true, BiomeCacheReset.class.getClassLoader());
            Field f = c.getDeclaredField(field);
            f.setAccessible(true);
            return (Map<?, ?>) f.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Bons and Furious: distanthorizons_world_change_biome_reset could not reach {}.{} ({}); that cache is left as it is", owner, field, e.toString());
            }
            return null;
        }
    }

    private static boolean clientSide() {
        try {
            Object dist = Class.forName("net.neoforged.fml.loading.FMLEnvironment").getField("dist").get(null);
            return dist == null || "CLIENT".equals(dist.toString());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return true;    // outside FML (offline harness)
        }
    }
}
