package bons.furious.mixin.flywheel_level_attached;

import bons.furious.patch.flywheel_level_attached.AttachedPins;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.engine_room.flywheel.lib.util.LevelAttached;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * flywheel_level_attached_weak_keys (Flywheel 1.0.5 inside Create 6.0.8), client only. [FIX]
 *
 * LevelAttached's constructor builds its Guava cache as CacheBuilder.newBuilder().removalListener(listener).build(loader).
 * The removalListener call is wrapped: Flywheel's listener is handed on behind one that releases the value's pin and skips
 * entries Guava drops because key and value were collected; a token for this cache is handed to the build call (a
 * thread-local paired with this builder, see AttachedPins.token). The build call is wrapped: weak keys and weak values on
 * the same builder, and Flywheel's own loader behind one that pins each loaded value on its key
 * (bons.furious.patch.flywheel_level_attached.AttachedPins). With the switch off neither call changes. No Flywheel code is
 * carried.
 */
@Mixin(value = LevelAttached.class, remap = false)
public abstract class LevelAttachedWeakKeysMixin {
    @WrapOperation(method = "<init>(Ljava/util/function/Function;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lcom/google/common/cache/CacheBuilder;removalListener(Lcom/google/common/cache/RemovalListener;)Lcom/google/common/cache/CacheBuilder;"))
    private CacheBuilder<?, ?> bons$pinnedListener(CacheBuilder<?, ?> builder, RemovalListener<?, ?> listener, Operation<CacheBuilder<?, ?>> original) {
        return original.call(builder, AttachedPins.listener(builder, listener));
    }

    @WrapOperation(method = "<init>(Ljava/util/function/Function;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lcom/google/common/cache/CacheBuilder;build(Lcom/google/common/cache/CacheLoader;)Lcom/google/common/cache/LoadingCache;"))
    private LoadingCache<?, ?> bons$pinnedBuild(CacheBuilder<?, ?> builder, CacheLoader<?, ?> loader, Operation<LoadingCache<?, ?>> original) {
        AttachedPins.Token t = AttachedPins.token(builder);
        return original.call(AttachedPins.builder(builder, t), AttachedPins.loader(loader, t));
    }
}
