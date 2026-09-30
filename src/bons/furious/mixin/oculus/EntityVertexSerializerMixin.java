package bons.furious.mixin.oculus;

import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.irisshaders.iris.compat.sodium.impl.vertex_format.ModelToEntityVertexSerializer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.NormalHelper;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * oculus_entity_vertex_reuse (Oculus 1.8.0 for Minecraft 1.20.1).
 *
 * serialize converts Sodium model vertices (36 bytes) to Oculus' entity vertex format for every quad drawn through
 * it. It read each quad's UVs twice (once for the tangent, once for the mid-texture coordinate), asked
 * CapturedRenderingState for the entity, block entity and item ids and the entity format for its stride once per
 * vertex, and copied every vertex through a generic memCopy. The values are now read once per quad and reused, and
 * the fixed 36-byte vertex is copied with four long and one int load/store (overlapping ranges still use memCopy).
 * The mid-texture sums keep the original vertex order, so the float results are identical.
 */
@Mixin(value = ModelToEntityVertexSerializer.class, remap = false)
public abstract class EntityVertexSerializerMixin {
    /**
     * @author BonsUnleashed
     * @reason Read each quad's shared values once and copy vertices without a memCopy call.
     */
    @Overwrite
    public void serialize(long src, long dst, int vertexCount) {
        for (int quad = 0; quad < vertexCount / 4; quad++) {
            int normal = MemoryUtil.memGetInt(src + 32L);
            float normalX = NormI8.unpackX(normal);
            float normalY = NormI8.unpackY(normal);
            float normalZ = NormI8.unpackZ(normal);
            float x0 = MemoryUtil.memGetFloat(src);
            float y0 = MemoryUtil.memGetFloat(src + 4L);
            float z0 = MemoryUtil.memGetFloat(src + 8L);
            float u0 = MemoryUtil.memGetFloat(src + 16L);
            float v0 = MemoryUtil.memGetFloat(src + 20L);
            float x1 = MemoryUtil.memGetFloat(src + 36L);
            float y1 = MemoryUtil.memGetFloat(src + 40L);
            float z1 = MemoryUtil.memGetFloat(src + 44L);
            float u1 = MemoryUtil.memGetFloat(src + 52L);
            float v1 = MemoryUtil.memGetFloat(src + 56L);
            float x2 = MemoryUtil.memGetFloat(src + 72L);
            float y2 = MemoryUtil.memGetFloat(src + 76L);
            float z2 = MemoryUtil.memGetFloat(src + 80L);
            float u2 = MemoryUtil.memGetFloat(src + 88L);
            float v2 = MemoryUtil.memGetFloat(src + 92L);
            float midU = 0.0F;
            float midV = 0.0F;
            midU += u0;
            midV += v0;
            midU += u1;
            midV += v1;
            midU += u2;
            midV += v2;
            midU += MemoryUtil.memGetFloat(src + 124L);
            midV += MemoryUtil.memGetFloat(src + 128L);
            midU /= 4.0F;
            midV /= 4.0F;
            int tangent = NormalHelper.computeTangent(normalX, normalY, normalZ, x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2);
            short entity = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
            short blockEntity = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
            short item = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
            int stride = IrisVertexFormats.ENTITY.m_86020_();
            for (int vertex = 0; vertex < 4; vertex++) {
                ac$copyModelVertex(src, dst);
                MemoryUtil.memPutShort(dst + 36L, entity);
                MemoryUtil.memPutShort(dst + 38L, blockEntity);
                MemoryUtil.memPutShort(dst + 40L, item);
                MemoryUtil.memPutFloat(dst + 42L, midU);
                MemoryUtil.memPutFloat(dst + 46L, midV);
                MemoryUtil.memPutInt(dst + 50L, tangent);
                src += 36L;
                dst += stride;
            }
        }
    }

    /** Copies one 36-byte model vertex; ranges closer than 36 bytes keep memCopy's overlap handling. */
    @Unique
    private static void ac$copyModelVertex(long src, long dst) {
        if (Long.compareUnsigned(dst - src, 36L) < 0 || Long.compareUnsigned(src - dst, 36L) < 0) {
            MemoryUtil.memCopy(src, dst, 36L);
            return;
        }
        long bytes0 = MemoryUtil.memGetLong(src);
        long bytes8 = MemoryUtil.memGetLong(src + 8L);
        long bytes16 = MemoryUtil.memGetLong(src + 16L);
        long bytes24 = MemoryUtil.memGetLong(src + 24L);
        int bytes32 = MemoryUtil.memGetInt(src + 32L);
        MemoryUtil.memPutLong(dst, bytes0);
        MemoryUtil.memPutLong(dst + 8L, bytes8);
        MemoryUtil.memPutLong(dst + 16L, bytes16);
        MemoryUtil.memPutLong(dst + 24L, bytes24);
        MemoryUtil.memPutInt(dst + 32L, bytes32);
    }
}
