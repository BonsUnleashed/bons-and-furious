package bons.furious.mixin.biomeswevegone;

import bons.furious.patch.biomeswevegone.ForeignChunkTerrain;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * biomeswevegone_foreign_chunk_skip: keeps the surface step's WorldGenRegion for this thread before BWG's own handler at
 * the same RETURN runs its terrain passes (priority 900: applied before BWG's default 1000, so its call comes first).
 * ForeignChunkTerrain only uses it for a pass whose chunk is the region's centre, so a wrong order only means the pass
 * runs as before.
 */
@Mixin(value = ChunkStatus.class, remap = false, priority = 900)
public abstract class SurfaceRegionCaptureMixin {
    @Inject(method = "m_156246_", at = @At("RETURN"))
    private static void bons$keepRegion(CallbackInfo ci, @Local WorldGenRegion region) {
        ForeignChunkTerrain.surfaceStarted(region);
    }
}
