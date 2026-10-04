package bons.furious.mixin.oculus_outline;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * oculus_shadow_outline_discard (Minecraft 1.21.1 OutlineBufferSource, client only): read access to the outline batch's own
 * immediate source (outlineBufferSource, created by the field initialiser with MultiBufferSource.immediate(new
 * ByteBufferBuilder(1536)); a mod that overwrites immediate() gives another class, which ShadowOutlineDiscard leaves alone).
 * Accessor only; see ShadowOutlineDiscard. Ported to 1.21.1: same field (1.20.1: built on new BufferBuilder(256)).
 */
@Mixin(value = OutlineBufferSource.class, remap = false)
public interface OutlineSourceAccessor {
    @Accessor("outlineBufferSource")
    MultiBufferSource.BufferSource bons$outlineSource();
}
