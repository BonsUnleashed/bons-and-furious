package bons.portprobe.mixin;

import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Development-only (never shipped), 1.1.0+mc1.21.1 port: stands in for another mod that injects into Embeddium's
 * DefaultChunkRenderer.fillCommandBuffer (as Chunks Fade In does for its fade), so the qualification can run the stand-down
 * path of bug sweep 5's F44 fix without a compat row for it: an @Inject at the method's HEAD that does nothing. Applied only with
 * -Dbons_and_furious.foreignFill110=true (ProbeMixinPlugin); in every other fixture it is left out.
 */
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class ForeignFillProbe110 {
    @Inject(method = "fillCommandBuffer", at = @At("HEAD"), require = 0)
    private static void bons$probeFill(MultiDrawBatch batch, RenderRegion region, SectionRenderDataStorage storage, ChunkRenderList list,
                                       CameraTransform camera, TerrainRenderPass pass, boolean culling, CallbackInfo ci) {
        // nothing: the method now carries another mod's handler, which is all the fixture needs
    }
}
