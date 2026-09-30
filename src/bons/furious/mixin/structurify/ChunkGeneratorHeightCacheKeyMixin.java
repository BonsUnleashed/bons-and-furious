package bons.furious.mixin.structurify;

import bons.furious.patch.structurify.WeakHeightAccessor;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.LevelHeightAccessor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * structurify_height_cache (Structurify 2.0.34+mc1.20.1), part 2 of 2: the cache key.
 *
 * Structurify's per-thread height cache keys each entry by the LevelHeightAccessor (the chunk or region being
 * generated) and kept it in a strong field, so every cached height pinned an otherwise finished chunk until the LRU map
 * evicted the entry. The key now holds the accessor through a {@link WeakHeightAccessor}; its hash is unchanged, and the
 * accessor still compares by identity, but a key whose accessor has been collected only equals itself, so the cache
 * can find and drop it (see ChunkGeneratorHeightCacheMixin).
 */
@Mixin(targets = "com.faboslav.structurify.common.world.level.chunk.ChunkGeneratorHeightCacheKey", remap = false)
public abstract class ChunkGeneratorHeightCacheKeyMixin {
    /** Null when the key was built without an accessor. Replaces the strong heightAccessor field, which stays null. */
    @Unique
    private WeakHeightAccessor bons$accessor;

    /** The constructor stores the accessor in the weak reference instead of the strong field. */
    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lcom/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCacheKey;heightAccessor:Lnet/minecraft/world/level/LevelHeightAccessor;"))
    private void bons$holdAccessorWeakly(@Coerce Object key, LevelHeightAccessor accessor) {
        this.bons$accessor = WeakHeightAccessor.of(accessor, key);
    }

    /**
     * Structurify's equals still compares the other fields (and the heightAccessor fields, now both null). When those
     * match, the accessors must be the same live object, or both keys must have been built without one.
     */
    @ModifyReturnValue(method = "equals", at = @At("RETURN"))
    private boolean bons$compareAccessors(boolean equal, @Local(argsOnly = true) Object object) {
        if (!equal || object == this) {
            return equal;
        }
        return WeakHeightAccessor.sameAccessor(this.bons$accessor, ((ChunkGeneratorHeightCacheKeyMixin) object).bons$accessor);
    }
}
