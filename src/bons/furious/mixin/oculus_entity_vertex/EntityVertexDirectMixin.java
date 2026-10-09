package bons.furious.mixin.oculus_entity_vertex;

import bons.furious.patch.oculus_entity_vertex.EntityVertexDirect;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferVertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * oculus_entity_vertex_direct (Oculus 1.8.0 + Embeddium 0.3.31 on Minecraft 1.20.1 / Forge 47.4.16, client only).
 *
 * With a shader pack, Oculus begins every NEW_ENTITY BufferBuilder with its own IrisVertexFormats.ENTITY instead (56 bytes:
 * NEW_ENTITY's elements, then iris_Entity, mc_midTexCoord, at_tangent, Padding2). BufferBuilder.vertex(14 values) (m_5954_)
 * writes directly only for the two vanilla formats it knows (NEW_ENTITY, BLOCK); for ENTITY it falls to VertexConsumer's
 * default: vertex(double x3), color(float x4), uv, overlayCoords, uv2, normal, endVertex - seven interface calls, each one
 * re-reading the current element and checking its usage, type and count, then nextElement(). Every EMF cube, GeckoLib
 * quad, Citadel box and item quad rendered through Oculus's batched entity buffers takes that path (a 64-mob profiling
 * scene: the default method 9.6 % self, 20.5 % inclusive of the render thread).
 *
 * This redirects that one call (the super call in m_5954_'s element branch: reached only when the format is not one of the
 * two vanilla fast formats and no default colour is set, after Oculus's SeparateAo @ModifyVariable has already replaced
 * the alpha at HEAD). For a plain BufferBuilder (not a subclass) whose format is IrisVertexFormats.ENTITY, it performs the
 * element path's steps itself, in the same order: before each element it checks that the builder's current element is the
 * one the element path's checks would accept (identity of the DefaultVertexFormat element), writes the same values through
 * BufferBuilder's own put methods at the same local offsets (positions through float->double->float as vertex(double)
 * does, colour as (byte)(int)(c * 255), overlay and light as their two shorts, the normal through
 * BufferVertexConsumer.normalIntValue), and then calls the builder's own nextElement(), which is Embeddium's overwrite
 * when Embeddium's mixin is on (it skips the padding byte with the Normal step). The padding byte is never written, as in
 * the element path. After the sixth step it calls endVertex() exactly as the default method does, so Oculus's
 * iris$beforeNext (ids, midTexCoord, tangent, fillExtendedData per quad), Colorwheel's and Oculus SegmentRendering's
 * endVertex hooks run unchanged. If any check fails (an abandoned vertex left the cursor elsewhere), it continues with the
 * element path's own remaining calls from that step; any other builder, format or state takes the original call.
 * Why identical: the same writes at the same offsets, the same nextElement() calls on the same object, the same
 * endVertex(); only the interface dispatch and the per-element checks whose outcome the identity check fixes are gone.
 */
@Mixin(value = BufferBuilder.class, priority = 1100, remap = false)
public abstract class EntityVertexDirectMixin extends DefaultedVertexConsumer implements BufferVertexConsumer {
    @Shadow
    private ByteBuffer f_85648_;
    @Shadow
    private int f_85652_;
    @Shadow
    private VertexFormatElement f_85655_;
    @Shadow
    private VertexFormat f_85658_;
    @Shadow
    private VertexFormat.Mode f_85657_;

    @Shadow
    public abstract void m_5832_(int index, float value);

    @Shadow
    public abstract void m_5672_(int index, byte value);

    @Shadow
    public abstract void m_5586_(int index, short value);

    @Shadow
    public abstract void m_5751_();

    @Shadow
    public abstract void m_5752_();

    @Redirect(method = "m_5954_", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/DefaultedVertexConsumer;m_5954_(FFFFFFFFFIIFFF)V"))
    private void bons$entityVertex(DefaultedVertexConsumer self, float x, float y, float z, float r, float g, float b, float a,
                                   float u, float v, int overlay, int light, float nx, float ny, float nz) {
        if (!EntityVertexDirect.enabled || this.f_85658_ != IrisVertexFormats.ENTITY || ((Object) this).getClass() != BufferBuilder.class
                || this.f_85655_ != DefaultVertexFormat.f_85804_) {
            super.m_5954_(x, y, z, r, g, b, a, u, v, overlay, light, nx, ny, nz);
            return;
        }
        if (EntityVertexDirect.SHADOW) {
            // the original path runs; the bytes it wrote are compared with what the direct path writes (the buffer is read
            // after the call: endVertex may have moved it to a larger allocation)
            int base = this.f_85652_;
            VertexFormat.Mode mode = this.f_85657_;
            super.m_5954_(x, y, z, r, g, b, a, u, v, overlay, light, nx, ny, nz);
            EntityVertexDirect.shadowCompare(this.f_85648_, base, mode, x, y, z, r, g, b, a, u, v, overlay, light, nx, ny, nz);
            return;
        }
        if (!EntityVertexDirect.announced) EntityVertexDirect.announce();
        // Position (BufferVertexConsumer.vertex(double, double, double) after VertexConsumer's float->double widening)
        this.m_5832_(0, (float) (double) x);
        this.m_5832_(4, (float) (double) y);
        this.m_5832_(8, (float) (double) z);
        this.m_5751_();
        if (this.f_85655_ != DefaultVertexFormat.f_85805_) {
            EntityVertexDirect.continueFrom(this, 1, r, g, b, a, u, v, overlay, light, nx, ny, nz);
            return;
        }
        // Color (VertexConsumer.color(float x4) -> BufferBuilder.color(int x4), default colour not set here)
        this.m_5672_(0, (byte) (int) (r * 255.0F));
        this.m_5672_(1, (byte) (int) (g * 255.0F));
        this.m_5672_(2, (byte) (int) (b * 255.0F));
        this.m_5672_(3, (byte) (int) (a * 255.0F));
        this.m_5751_();
        if (this.f_85655_ != DefaultVertexFormat.f_85806_) {
            EntityVertexDirect.continueFrom(this, 2, r, g, b, a, u, v, overlay, light, nx, ny, nz);
            return;
        }
        // UV0
        this.m_5832_(0, u);
        this.m_5832_(4, v);
        this.m_5751_();
        if (this.f_85655_ != DefaultVertexFormat.f_85807_) {
            EntityVertexDirect.continueFrom(this, 3, r, g, b, a, u, v, overlay, light, nx, ny, nz);
            return;
        }
        // UV1 (overlayCoords(int) -> overlayCoords(int, int) -> uvShort((short), (short), 1))
        this.m_5586_(0, (short) (overlay & 65535));
        this.m_5586_(2, (short) (overlay >> 16 & 65535));
        this.m_5751_();
        if (this.f_85655_ != DefaultVertexFormat.f_85808_) {
            EntityVertexDirect.continueFrom(this, 4, r, g, b, a, u, v, overlay, light, nx, ny, nz);
            return;
        }
        // UV2 (uv2(int) -> uv2(int, int) -> uvShort((short), (short), 2))
        this.m_5586_(0, (short) (light & 65535));
        this.m_5586_(2, (short) (light >> 16 & 65535));
        this.m_5751_();
        if (this.f_85655_ != DefaultVertexFormat.f_85809_) {
            EntityVertexDirect.continueFrom(this, 5, r, g, b, a, u, v, overlay, light, nx, ny, nz);
            return;
        }
        // Normal
        this.m_5672_(0, BufferVertexConsumer.m_85774_(nx));
        this.m_5672_(1, BufferVertexConsumer.m_85774_(ny));
        this.m_5672_(2, BufferVertexConsumer.m_85774_(nz));
        this.m_5751_();
        this.m_5752_();
    }
}
