package bons.furious.mixin.oculus_outline;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.Map;
import java.util.SequencedMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * oculus_shadow_outline_discard (Minecraft 1.21.1 MultiBufferSource$BufferSource, client only): access to the shared byte
 * buffer (sharedBuffer), the fixed buffers (fixedBuffers), the started builders (startedBuilders) and the render type of
 * the batch on the shared buffer (lastSharedType), so that ShadowOutlineDiscard can make endLastBatch()'s state changes
 * without its draw. Accessor only; method names carry the bons$ prefix (Iris has its own accessor on this class).
 *
 * Ported to 1.21.1: the 1.21 buffer source keeps one BufferBuilder per started render type over a ByteBufferBuilder
 * (1.20.1: one shared BufferBuilder, a started set and an Optional last state), so the accessors follow the new fields.
 */
@Mixin(value = MultiBufferSource.BufferSource.class, remap = false)
public interface BufferSourceAccessor {
    @Accessor("sharedBuffer")
    ByteBufferBuilder bons$sharedBuffer();

    @Accessor("fixedBuffers")
    SequencedMap<RenderType, ByteBufferBuilder> bons$fixedBuffers();

    @Accessor("startedBuilders")
    Map<RenderType, BufferBuilder> bons$startedBuilders();

    @Accessor("lastSharedType")
    RenderType bons$lastSharedType();

    @Accessor("lastSharedType")
    void bons$setLastSharedType(RenderType type);
}
