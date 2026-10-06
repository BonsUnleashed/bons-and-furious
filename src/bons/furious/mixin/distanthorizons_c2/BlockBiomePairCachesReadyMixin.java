package bons.furious.mixin.distanthorizons_c2;

import bons.furious.patch.distanthorizons_c2.BiomeCacheReset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * distanthorizons_world_change_biome_reset (Distant Horizons 3.3.2, LGPL-3.0; both sides), 1.0.34: BlockBiomeWrapperPair
 * reports the end of its static initializer, after which {@link BiomeCacheReset} may read its pair cache without loading
 * or initializing the class.
 */
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair", remap = false)
public abstract class BlockBiomePairCachesReadyMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"), require = 0, expect = 0)
    private static void bons$cachesReady(CallbackInfo ci) {
        BiomeCacheReset.pairReady();
    }
}
