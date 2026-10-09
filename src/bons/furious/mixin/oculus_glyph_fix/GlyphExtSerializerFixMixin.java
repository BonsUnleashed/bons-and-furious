package bons.furious.mixin.oculus_glyph_fix;

import bons.furious.patch.oculus_glyph_fix.GlyphFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * oculus_glyph_extended_vertex_fix (Oculus 1.8.0 + Embeddium 0.3.31 on Minecraft 1.20.1 / Forge 47.4.16, client only). Fix.
 *
 * With a shader pack, Oculus extends text BufferBuilders to its GLYPH format (52 bytes: position, colour, UV0, light, normal,
 * iris_Entity, mc_midTexCoord, at_tangent). Embeddium writes each glyph quad as 4 POSITION_COLOR_TEX_LIGHTMAP vertices
 * through VertexBufferWriter.push, and Oculus's GlyphExtVertexSerializer.serialize converts them: it copies each vertex,
 * adds the ids, and calls endQuad(uSum, vSum, src, dst) with src and dst already advanced past the 4th vertex. endQuad
 * expects the start of the quad's LAST vertex (its QuadView reads vertex i at p - stride * (3 - i), and it writes vertex k
 * at p - stride * k), so: the face normal and tangent are computed from the bytes after the source quad read at the GLYPH
 * stride (not the quad), and the normal, tangent and midTexCoord are written into vertices 1-3 and into the slot after the
 * quad, while vertex 0 keeps whatever was in the buffer. (In-world text under shaders: name tags, signs, map labels.)
 *
 * This redirects that one call: when serialize converted exactly one quad (vertexCount 4, captured from serialize's
 * arguments; Embeddium's glyph writer always pushes 4), endQuad gets the start of the quad's last vertex in the written GLYPH data for
 * both its reading and its writing pointer: the same normal, tangent and midTexCoord Oculus's own BufferBuilder path
 * (fillExtendedData) writes for a GLYPH quad, in all four vertices, and nothing after the quad. Other counts keep the
 * original call. Positions, colours, UVs, light and ids are untouched.
 */
@Mixin(targets = "net.irisshaders.iris.compat.sodium.impl.vertex_format.GlyphExtVertexSerializer", remap = false)
public abstract class GlyphExtSerializerFixMixin {
    @Shadow(remap = false)
    private static void endQuad(float uSum, float vSum, long src, long dst) {
        throw new AssertionError();
    }

    @Redirect(method = "serialize", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/compat/sodium/impl/vertex_format/GlyphExtVertexSerializer;endQuad(FFJJ)V"), remap = false)
    private void bons$endQuad(float uSum, float vSum, long src, long dst, long src0, long dst0, int vertexCount) {
        if (GlyphFix.enabled && vertexCount == 4) {
            long last = dst - GlyphFix.STRIDE;
            GlyphFix.fixed();
            endQuad(uSum, vSum, last, last);
            return;
        }
        endQuad(uSum, vSum, src, dst);
    }
}
