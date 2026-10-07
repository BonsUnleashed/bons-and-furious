package bons.furious.mixin.distanthorizons_c2;

import bons.furious.patch.distanthorizons_c2.BiomeCacheReset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * distanthorizons_world_change_biome_reset (Distant Horizons 3.3.3 (1.21.1), LGPL-3.0; client), 1.0.34: AbstractDhTintGetter_neoforge
 * reports the end of its static initializer, after which {@link BiomeCacheReset} may read its two client caches without
 * loading or initializing the class.
 */
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.block.AbstractDhTintGetter_neoforge", remap = false)
public abstract class TintCachesReadyMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"), require = 0, expect = 0)
    private static void bons$cachesReady(CallbackInfo ci) {
        BiomeCacheReset.tintReady();
    }
}
