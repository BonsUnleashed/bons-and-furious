package bons.furious.mixin.biomeswevegone;

import bons.furious.patch.biomeswevegone.ForeignChunkTerrain;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * biomeswevegone_foreign_chunk_skip: forgets the kept WorldGenRegion after BWG's handler at the same RETURN (priority
 * 1100: applied after BWG's default 1000, so its call comes last), so no region outlives its surface step.
 */
@Mixin(value = ChunkStatus.class, remap = false, priority = 1100)
public abstract class SurfaceRegionReleaseMixin {
    @Inject(method = "m_156246_", at = @At("RETURN"))
    private static void bons$forgetRegion(CallbackInfo ci) {
        ForeignChunkTerrain.surfaceFinished();
    }
}
