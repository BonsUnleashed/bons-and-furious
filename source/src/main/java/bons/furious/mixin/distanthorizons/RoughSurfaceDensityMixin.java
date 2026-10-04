package bons.furious.mixin.distanthorizons;

import bons.furious.patch.distanthorizons.RoughSurfaceDensity;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * distanthorizons_rough_surface_xz_cache (Distant Horizons 3.3.2).
 *
 * The rough-surface generator's parameters keep the level's final density for its surface probes. At the end of their
 * constructor the density is replaced by a RoughSurfaceDensity.Deferred, which on the first probe becomes the exact copy
 * with per-thread caches on the x/z-only parts (or the density as it is when that copy cannot be proven exact): a level
 * whose surface is never generated (every level of a dedicated server) never builds the copy. GenParams_neoforge is a
 * private nested class, hence the string target and the coerced constructor parameters.
 *
 * 1.0.29 heap fix ported to 1.21.1: deferred() instead of prepare() at the constructor return (DH 3.3.3 still assigns
 * density = randomState.router().finalDensity() as the constructor's last store and reads it only in
 * isNoiseSolidAtBlockPos).
 */
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.worldGeneration.DhRoughSurfaceGenerator$GenParams_neoforge", remap = false)
public abstract class RoughSurfaceDensityMixin {
    @Shadow
    public DensityFunction density;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$cacheTwoDimensionalParts(@Coerce Object chunkGenerator, @Coerce Object serverLevelWrapper, CallbackInfo ci) {
        this.density = RoughSurfaceDensity.deferred(this.density);
    }
}
