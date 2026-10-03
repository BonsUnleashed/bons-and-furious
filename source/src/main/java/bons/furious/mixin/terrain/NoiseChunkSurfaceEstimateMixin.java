package bons.furious.mixin.terrain;

import bons.pure.terrain.SurfaceEstimateShare;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * terrain_surface_estimate_share (Minecraft 1.20.1 world generation).
 *
 * NoiseChunk.computePreliminarySurfaceLevel scans a column's initial density from the top down in cell-height steps.
 * Every NoiseChunk keeps its own results, so neighbouring chunks and single-column height queries scan the same columns
 * again. The method now asks SurfaceEstimateShare first and returns a stored value without scanning; a value it does
 * scan is stored on the way out. The helper answers "not stored" wherever the result could depend on the NoiseChunk,
 * and then the original scan runs.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkSurfaceEstimateMixin {
    /** Start of computePreliminarySurfaceLevel: a stored estimate for this column replaces the scan. */
    @Inject(method = "computePreliminarySurfaceLevel", at = @At("HEAD"), cancellable = true)
    private void bons$storedEstimate(long column, CallbackInfoReturnable<Integer> cir) {
        int stored = SurfaceEstimateShare.get((NoiseChunk) (Object) this, column);
        if (stored != SurfaceEstimateShare.MISSING) cir.setReturnValue(stored);
    }

    /** Every return of the scan: store the estimate and return it unchanged. */
    @ModifyReturnValue(method = "computePreliminarySurfaceLevel", at = @At("RETURN"))
    private int bons$storeEstimate(int estimate, long column) {
        return SurfaceEstimateShare.put((NoiseChunk) (Object) this, column, estimate);
    }
}
