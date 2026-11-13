package bons.furious.mixin.embeddium_hidden_blocks;

import org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockOcclusionCache;
import org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * embeddium_hidden_block_skip: the block renderer's own occlusion cache (BlockRenderer.occlusionCache), the object its
 * isFaceVisible asks, so the hidden test gives the answers the renderer would get. Ported to 1.21.1: the same field of
 * Embeddium 1.0.15's BlockRenderer (package org.embeddedt.embeddium.impl.render.chunk.compile.pipeline).
 */
@Mixin(value = BlockRenderer.class, remap = false)
public interface BlockRendererOcclusionAccessor {
    @Accessor(value = "occlusionCache", remap = false)
    BlockOcclusionCache bons$occlusionCache();
}
