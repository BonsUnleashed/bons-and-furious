package bons.furious.mixin.embeddium_draw;

import bons.furious.patch.embeddium_draw.DrawBatchCache;
import bons.furious.patch.embeddium_draw.MergedDraws;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * embeddium_draw_batch_cache (Embeddium 1.0.15+mc1.21.1 on Minecraft 1.21.1 / NeoForge 21.1.252; client only; first
 * written for Embeddium 0.3.31+mc1.20.1).
 *
 * Ported to 1.21.1: names only (DefaultChunkRenderer.render and fillCommandBuffer are the 0.3.31 code under
 * org.embeddedt.embeddium.impl; same call, same seven arguments). No Oculus shadow pass exists on 1.21.1 (Iris 1.8.12
 * refuses Embeddium); the culling flag is Embeddium's option.
 *
 * Wraps the fillCommandBuffer call in DefaultChunkRenderer.render. Each SectionRenderDataStorage (one per region and terrain
 * pass) remembers its last two filled command lists with everything fillCommandBuffer reads (DrawBatchCache): the
 * storage's render-data version, the render list's section indices with geometry, the pass's reverse/sorted flags, the
 * face-culling flag passed in (Oculus turns it off in the shadow pass), the camera's face-test bits for the region, and
 * whether embeddium_merged_draws merges. When all are equal the remembered commands are copied into the batch instead
 * of refilling it; otherwise the fill runs as shipped (with the merge, when on) and is remembered. Priority 1100: applied
 * after DefaultChunkRendererMergeMixin, so this wrapper is the outer one and a cache hit skips the merge too (the
 * remembered commands were merged when they were filled).
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false, priority = 1100)
public abstract class DefaultChunkRendererBatchCacheMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer;fillCommandBuffer(Lorg/embeddedt/embeddium/impl/gl/device/MultiDrawBatch;Lorg/embeddedt/embeddium/impl/render/chunk/region/RenderRegion;Lorg/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderList;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;Z)V"))
    private void bons$cachedFill(MultiDrawBatch batch, RenderRegion region, SectionRenderDataStorage storage, ChunkRenderList list, CameraTransform camera,
                                 TerrainRenderPass pass, boolean culling, Operation<Void> original) {
        if (!DrawBatchCache.enabled || storage == null || list == null) {
            original.call(batch, region, storage, list, camera, pass, culling);
            return;
        }
        DrawBatchCache.Slots slots = ((DrawBatchCache.Host) storage).bons$drawSlots();
        int count = list.getSectionsWithGeometryCount();
        byte[] sections = ((ChunkRenderListAccessMixin) list).bons$geometry();
        boolean reverse = pass.isReverseOrder(), sorted = pass.isSorted();
        boolean merged = MergedDraws.active(sorted) && !MergedDraws.SHADOW;
        long face = culling && !sorted && count > 0 ? DrawBatchCache.faceBits(region, camera) : 0L;
        DrawBatchCache.Entry hit = slots.find(count, sections, reverse, sorted, culling, merged, face);
        if (hit != null && !DrawBatchCache.SHADOW) {
            hit.load(batch);
            DrawBatchCache.hit();
            if (merged) MergedDraws.count(hit.sourceSize(), hit.size(), culling);
            return;
        }
        original.call(batch, region, storage, list, camera, pass, culling);
        if (hit != null) {
            DrawBatchCache.SHADOW_CHECKS.incrementAndGet();
            if (!hit.matches(batch)) {
                DrawBatchCache.mismatch("region " + region.getChunkX() + "," + region.getChunkY() + "," + region.getChunkZ() + " pass " + pass
                        + ": remembered " + hit.size() + " commands, refilled " + batch.size);
            }
            return;
        }
        DrawBatchCache.miss();
        slots.victim().store(batch, slots.version, count, sections, reverse, sorted, culling, merged, face, merged ? MergedDraws.lastFilled : batch.size);
    }
}
