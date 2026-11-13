package bons.furious.mixin.embeddium_hidden_blocks;

import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * embeddium_hidden_block_skip: the block renderer's own occlusion cache (BlockRenderer.occlusionCache), the object its
 * isFaceVisible asks, so the hidden test gives the answers the renderer would get.
 */
@Mixin(value = BlockRenderer.class, remap = false)
public interface BlockRendererOcclusionAccessor {
    @Accessor(value = "occlusionCache", remap = false)
    BlockOcclusionCache bons$occlusionCache();
}
