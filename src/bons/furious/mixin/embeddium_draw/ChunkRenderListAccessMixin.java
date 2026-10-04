package bons.furious.mixin.embeddium_draw;

import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * embeddium_draw_batch_cache (Embeddium 0.3.31+mc1.20.1; client only): read access to a render list's array of section
 * indices with geometry (the first getSectionsWithGeometryCount() entries are the list), which is one of the inputs of
 * fillCommandBuffer and therefore part of a remembered command list's key. Read only.
 */
@Mixin(value = ChunkRenderList.class, remap = false)
public interface ChunkRenderListAccessMixin {
    @Accessor("sectionsWithGeometry")
    byte[] bons$geometry();
}
