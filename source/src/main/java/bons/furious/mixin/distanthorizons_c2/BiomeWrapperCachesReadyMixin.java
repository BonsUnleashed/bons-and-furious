package bons.furious.mixin.distanthorizons_c2;

import bons.furious.patch.distanthorizons_c2.BiomeCacheReset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * distanthorizons_world_change_biome_reset (Distant Horizons 3.3.3 (1.21.1), LGPL-3.0; both sides), 1.0.34: BiomeWrapper_neoforge
 * reports the end of its static initializer, after which {@link BiomeCacheReset} may read its two biome maps without
 * loading or initializing the class (the reset used to load it inside setDhWorld's network window; see there).
 */
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper_neoforge", remap = false)
public abstract class BiomeWrapperCachesReadyMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"), require = 0, expect = 0)
    private static void bons$cachesReady(CallbackInfo ci) {
        BiomeCacheReset.wrapperReady();
    }
}
