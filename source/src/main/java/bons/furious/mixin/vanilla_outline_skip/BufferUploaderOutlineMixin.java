package bons.furious.mixin.vanilla_outline_skip;

import bons.furious.patch.vanilla_outline_skip.OutlineSkip;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_entity_outline_composite_skip (tested build: Minecraft 1.21.1 / NeoForge 21.1.252, client only; first written
 * for Minecraft 1.20.1 / Forge 47.4.16): BufferUploader.draw(MeshData) binds and fills the format's immediate vertex
 * buffer, then draws it. While OutlineSkip.skipDraw is set (only inside the skipped entity-outline composite's
 * blitToScreen) the composite's draw call is left out; the bind and upload before it (which also closes the MeshData) run
 * unchanged, so every tracked GL state is what it would have been. Otherwise the draw runs as before.
 *
 * Ported to 1.21.1: draw takes the 1.21 MeshData instead of BufferBuilder$RenderedBuffer and calls VertexBuffer.draw()
 * after upload(mesh) unconditionally (a MeshData is never empty). The composite now draws with DefaultVertexFormat
 * .BLIT_SCREEN, a format only RenderTarget._blitToScreen uses, so the draw is left out only when the uploaded buffer's
 * format is BLIT_SCREEN, and only once per skipped composite: a draw some other code might make inside that window (none
 * is known) runs as usual.
 */
@Mixin(value = BufferUploader.class, remap = false)
public abstract class BufferUploaderOutlineMixin {
    @Redirect(method = "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"), remap = false)
    private static void bons$compositeDraw(VertexBuffer buffer) {
        if (OutlineSkip.skipDraw && buffer.getFormat() == DefaultVertexFormat.BLIT_SCREEN) {
            OutlineSkip.skipDraw = false;
            OutlineSkip.LEFT_OUT.incrementAndGet();
            return;
        }
        buffer.draw();
    }
}
