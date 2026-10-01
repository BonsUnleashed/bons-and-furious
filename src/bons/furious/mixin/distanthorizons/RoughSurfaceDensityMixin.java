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
 * constructor the density is replaced by RoughSurfaceDensity's exact copy with per-thread caches on the x/z-only parts
 * (or left as it is when that copy cannot be proven exact). GenParams_forge is a private nested class, hence the string
 * target and the coerced constructor parameters.
 */
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.worldGeneration.DhRoughSurfaceGenerator$GenParams_forge", remap = false)
public abstract class RoughSurfaceDensityMixin {
    @Shadow
    public DensityFunction density;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$cacheTwoDimensionalParts(@Coerce Object chunkGenerator, @Coerce Object serverLevelWrapper, CallbackInfo ci) {
        this.density = RoughSurfaceDensity.prepare(this.density);
    }
}
