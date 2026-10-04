package bons.furious.mixin.dh_loader;

import bons.furious.patch.dh_loader.PooledStringIndex;
import com.seibel.distanthorizons.core.util.objects.pooling.StringPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * distanthorizons_pooled_string_index (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge, LGPL-3.0; both sides).
 *
 * Insurance for the index's identity guarantee: StringPool.clear() (no caller in DH 3.3.2 or 3.3.3) would let the trie
 * create new String instances for texts the index already remembers, so the index steps aside for good before the pool
 * clears ({@link PooledStringIndex#poolCleared}). Runs once at most; the clear itself is unchanged. No DH code is carried.
 *
 * Ported to 1.21.1: no change (StringPool.clear is byte-identical in DH 3.3.3).
 */
@Mixin(value = StringPool.class, remap = false)
public abstract class StringPoolClearMixin {
    @Inject(method = "clear", at = @At("HEAD"))
    private void bons$indexStepsAside(CallbackInfo ci) {
        PooledStringIndex.poolCleared();
    }
}
