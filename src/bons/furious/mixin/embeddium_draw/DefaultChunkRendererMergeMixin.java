package bons.furious.mixin.embeddium_draw;

import bons.furious.patch.embeddium_draw.MergedDraws;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * embeddium_merged_draws (Embeddium 0.3.31+mc1.20.1; client only).
 *
 * After DefaultChunkRenderer.render's fillCommandBuffer call has filled a region's command list, the unsorted passes'
 * commands whose vertex ranges touch are merged in place (MergedDraws.merge): the same vertices, triangles and order, fewer
 * commands. Sorted (translucent) passes and shader packs that read per-draw inputs are left as filled (MergedDraws.active).
 * Priority 1000: the inner wrapper; embeddium_draw_batch_cache wraps around it and remembers the merged commands, so with
 * the cache on the merge runs once per changed fill instead of once per frame. The static field marks the renderer as
 * carrying this wrapper (set when DefaultChunkRenderer is initialised), so the cache keys its entries by whether the merge
 * runs.
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false, priority = 1000)
public abstract class DefaultChunkRendererMergeMixin {
    @Unique
    private static final boolean bons$mergeApplied = MergedDraws.markApplied();

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/DefaultChunkRenderer;fillCommandBuffer(Lme/jellysquid/mods/sodium/client/gl/device/MultiDrawBatch;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Lme/jellysquid/mods/sodium/client/render/chunk/data/SectionRenderDataStorage;Lme/jellysquid/mods/sodium/client/render/chunk/lists/ChunkRenderList;Lme/jellysquid/mods/sodium/client/render/viewport/CameraTransform;Lme/jellysquid/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Z)V"))
    private void bons$mergedFill(MultiDrawBatch batch, RenderRegion region, SectionRenderDataStorage storage, ChunkRenderList list, CameraTransform camera,
                                 TerrainRenderPass pass, boolean culling, Operation<Void> original) {
        original.call(batch, region, storage, list, camera, pass, culling);
        if (!MergedDraws.active(pass.isSorted())) return;
        if (MergedDraws.SHADOW) {
            MergedDraws.shadowCheck(batch, culling);
            return;
        }
        int before = MergedDraws.merge(batch);
        MergedDraws.count(before, batch.size, culling);
    }
}
