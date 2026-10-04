package bons.furious.mixin.oculus_outline;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * oculus_shadow_outline_discard (Minecraft 1.20.1 OutlineBufferSource, client only): read access to the outline batch's own
 * immediate source (f_109921_, created in the constructor with MultiBufferSource.immediate(new BufferBuilder(256)); with
 * ImmediatelyFast that call returns IF's BatchableBufferSource). Accessor only; see ShadowOutlineDiscard.
 */
@Mixin(value = OutlineBufferSource.class, remap = false)
public interface OutlineSourceAccessor {
    @Accessor("f_109921_")
    MultiBufferSource.BufferSource bons$outlineSource();
}
