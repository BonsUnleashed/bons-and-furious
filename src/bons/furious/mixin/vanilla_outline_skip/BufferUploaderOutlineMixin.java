package bons.furious.mixin.vanilla_outline_skip;

import bons.furious.patch.vanilla_outline_skip.OutlineSkip;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_entity_outline_composite_skip (Minecraft 1.20.1 / Forge 47.4.16, client only): BufferUploader.draw (m_231209_)
 * binds and fills the immediate vertex buffer, then draws it. While OutlineSkip.skipDraw is set (only inside the skipped
 * entity-outline composite's blitToScreen) the draw call is left out; the bind and upload before it run unchanged, so
 * every tracked GL state is what it would have been. Otherwise the draw runs as before.
 */
@Mixin(value = BufferUploader.class, remap = false)
public abstract class BufferUploaderOutlineMixin {
    @Redirect(method = "m_231209_", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;m_166882_()V"), remap = false)
    private static void bons$compositeDraw(VertexBuffer buffer) {
        if (OutlineSkip.skipDraw) return;
        buffer.m_166882_();
    }
}
