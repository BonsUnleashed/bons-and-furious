package bons.furious.mixin.oculus_outline;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * oculus_shadow_outline_discard (Minecraft 1.20.1 MultiBufferSource$BufferSource, client only): access to the shared builder
 * (f_109904_), the fixed buffers (f_109905_), the last render type (f_109906_) and the started builders (f_109907_), so that
 * ShadowOutlineDiscard can make endBatch(lastState)'s state changes without its draw. Accessor only; method names carry
 * the bons$ prefix (Oculus has its own accessor on this class).
 */
@Mixin(value = MultiBufferSource.BufferSource.class, remap = false)
public interface BufferSourceAccessor {
    @Accessor("f_109904_")
    BufferBuilder bons$builder();

    @Accessor("f_109905_")
    Map<RenderType, BufferBuilder> bons$fixedBuffers();

    @Accessor("f_109906_")
    Optional<RenderType> bons$lastState();

    @Accessor("f_109906_")
    void bons$setLastState(Optional<RenderType> state);

    @Accessor("f_109907_")
    Set<BufferBuilder> bons$startedBuffers();
}
