package bons.furious.mixin.structurify;

import bons.furious.patch.structurify.WeakHeightAccessor;
import com.faboslav.structurify.common.world.level.chunk.ChunkGeneratorHeightCache;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.lang.ref.Reference;
import java.util.Map;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * structurify_height_cache (Structurify 2.0.34+mc1.20.1), part 1 of 2: the per-thread cache.
 *
 * The cache keys now hold their LevelHeightAccessor weakly (ChunkGeneratorHeightCacheKeyMixin). Before every lookup and
 * insert, the entries whose accessor has been garbage collected are removed from the thread's map, and the accessor
 * passed in stays strongly reachable until the map operation is done, so a key cannot lose it half way.
 *
 * The two redirects take the target method's first five arguments (Mixin's argument capture) only to reach the accessor;
 * capturing it with MixinExtras' @Local here would allocate a LocalRef on every call.
 */
@Mixin(value = ChunkGeneratorHeightCache.class, remap = false)
public abstract class ChunkGeneratorHeightCacheMixin {
    /** Right after get and put fetch this thread's map: drop the keys whose accessor has been collected. */
    @ModifyExpressionValue(method = {"get", "put"}, require = 2,
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;"))
    private static Object bons$dropCollectedKeys(Object cache) {
        WeakHeightAccessor.drainCollectedAccessors((Map<?, ?>) cache);
        return cache;
    }

    /** The lookup itself, unchanged, with the accessor kept reachable until it returns. */
    @Redirect(method = "get", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$lookUp(Map<?, ?> cache, Object key,
                                      ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor) {
        try {
            return cache.get(key);
        } finally {
            Reference.reachabilityFence(accessor);
        }
    }

    /** The insert itself, unchanged, with the accessor kept reachable until it returns. */
    @Redirect(method = "put", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$insert(Map<Object, Object> cache, Object key, Object height,
                                      ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor) {
        try {
            return cache.put(key, height);
        } finally {
            Reference.reachabilityFence(accessor);
        }
    }
}
