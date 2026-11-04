package bons.furious.patch.biomancy_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch biomancy_spatial_query_memo (Biomancy 2.9.8.1-alpha.0, the tested build, and 2.8.19.0; server).
 *
 * Biomancy keeps the shapes of its cradles, membranes, flesh veins and neural interceptors in a spatial database and asks
 * it on spawn checks and block ticks. Each query first builds the dimension's key string, level.dimension().location()
 * .toString() (a new String from the namespace and the path), used only as a map key and in the database's map names
 * (2.9.8.1: SDBI.getLevelKey; 2.8.19.0: SpatialShapeManager.getLevelKey). In 2.8.19.0 each query also looks the database up
 * again (SpatialShapeStorage.getInstance: the world folder path resolved twice, turned into a File and a String, two new
 * lambdas and the overworld's saved-data lookup); 2.9.8.1 keeps its databases itself.
 *
 * The switch keeps each dimension's key string (a ResourceLocation never changes, and a dimension's ResourceKey and its
 * location are one object for the game's lifetime): the same characters, so every map lookup and map name is the same.
 * In 2.8.19.0 it also keeps the database object getInstance returned, per server (both held weakly): getInstance always
 * returns the one object the overworld's saved-data cache holds for "biomancy.spatial", which nothing removes while the
 * server runs; SpatialShapeStorage.close() (Biomancy's world unload) drops the kept one, so the next call asks Biomancy
 * again, as before. Identical results for every query. Runtime flag -Dbons_and_furious.biomancySpatialQueryMemo=false.
 * Shadow mode (.shadow=true): every kept key string is compared with a freshly built one (up to 20 mismatches logged).
 * Biomancy is LGPL-3.0; no code of it is carried.
 */
public final class SpatialMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.biomancySpatialQueryMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.biomancySpatialQueryMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** Copy-on-write: one entry per dimension location ever asked about. */
    private static volatile Map<ResourceLocation, String> keys = new IdentityHashMap<>();
    private static volatile Store store;
    private static volatile boolean announced;

    private record Store(WeakReference<MinecraftServer> server, WeakReference<Object> storage) {
    }

    private SpatialMemo() {
    }

    /** The dimension key string: the kept one, or Biomancy's own (then kept). */
    public static String levelKey(ResourceLocation location, Operation<String> original) {
        if (!enabled) return original.call(location);
        String key = keys.get(location);
        if (key == null) return keep(location, original.call(location));
        if (SHADOW) shadow(location, key, original);
        return key;
    }

    private static synchronized String keep(ResourceLocation location, String key) {
        Map<ResourceLocation, String> now = keys;
        String have = now.get(location);
        if (have != null) return have;
        IdentityHashMap<ResourceLocation, String> next = new IdentityHashMap<>(now);
        next.put(location, key);
        keys = next;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: biomancy_spatial_query_memo: Biomancy's spatial queries reuse each dimension's key");
        }
        return key;
    }

    private static void shadow(ResourceLocation location, String key, Operation<String> original) {
        SHADOW_CHECKS.incrementAndGet();
        String fresh = original.call(location);
        if (!fresh.equals(key)) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: biomancy_spatial_query_memo shadow mismatch #{}: kept '{}', built '{}'", n, key, fresh);
        }
    }

    /** 2.8.19.0 getInstance HEAD: the database kept for this level's server, or null (then Biomancy looks it up). */
    public static Object storage(ServerLevel level) {
        if (!enabled) return null;
        Store s = store;
        if (s == null) return null;
        MinecraftServer server = level.m_7654_();
        return server != null && s.server().get() == server ? s.storage().get() : null;
    }

    /** 2.8.19.0 getInstance RETURN (a lookup that ran): keep what Biomancy returned for this server. */
    public static void remember(ServerLevel level, Object storage) {
        if (!enabled || storage == null) return;
        MinecraftServer server = level.m_7654_();
        if (server == null) return;
        Store s = store;
        if (s != null && s.server().get() == server && s.storage().get() == storage) return;
        store = new Store(new WeakReference<>(server), new WeakReference<>(storage));
    }

    /** 2.8.19.0 SpatialShapeStorage.close TAIL: the closed database is not kept. */
    public static void forget(Object storage) {
        Store s = store;
        if (s != null && s.storage().get() == storage) store = null;
    }
}
