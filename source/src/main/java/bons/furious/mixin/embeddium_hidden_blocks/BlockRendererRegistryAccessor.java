package bons.furious.mixin.embeddium_hidden_blocks;

import java.util.List;
import org.embeddedt.embeddium.api.BlockRendererRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * embeddium_hidden_block_skip: the list of custom block renderer sources registered with Embeddium
 * (BlockRendererRegistry.renderPopulators; fillCustomRenderers asks each of them for every rendered block). While it is
 * empty no custom renderer can draw a block or see the block renderer's random source between two blocks.
 */
@Mixin(value = BlockRendererRegistry.class, remap = false)
public interface BlockRendererRegistryAccessor {
    @Accessor(value = "renderPopulators", remap = false)
    List<?> bons$renderPopulators();
}
