package bons.furious.patch.lionfishapi_bulk_c49;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.client.model.tools.LionfishModelRenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.jellysquid.mods.sodium.client.render.vertex.buffer.ExtendedBufferBuilder;
import me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/**
 * lionfishapi_model_bulk_vertices, the Embeddium side (loaded only when Embeddium is present; see BulkVertices). Embeddium is
 * LGPL; it is used through its public classes only. Render thread only (checked here), so the two vectors are reused.
 */
final class EmbeddiumBoxWriter {
    private static final Vector3f NORMAL = new Vector3f();
    private static final Vector4f POSITION = new Vector4f();
    /** Bytes of one quad in Embeddium's entity vertex format. */
    private static final int QUAD_BYTES = 4 * ModelVertex.STRIDE;

    private EmbeddiumBoxWriter() {
    }

    /**
     * True when the box was written into the consumer (exactly Embeddium's SodiumBufferBuilder: BulkVertices tested its
     * class) in one block; false when nothing was written (Lionfish's loop runs): another format or a fixed colour, another
     * thread, a shader pack, a box Lionfish's loop would fail on (a missing quad, normal, corner or position) or one that
     * does not fit the stack. The tests are small (inlined where the switch stands down); the loops are in writeBox.
     */
    static boolean write(AdvancedModelBox box, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
                         float red, float green, float blue, float alpha) {
        SodiumBufferBuilder fast = (SodiumBufferBuilder) consumer;
        if (!(fast.getOriginalBufferBuilder() instanceof ExtendedBufferBuilder target)
                || target.sodium$getFormatDescription() != ModelVertex.FORMAT || target.sodium$usingFixedColor()) return false;
        if (!LionfishFields.READY || !RenderSystem.isOnRenderThread() || BulkVertices.IRIS && ShaderCheck.shaderPackInUse()) return false;
        return writeBox(box.cubeList, pose, fast, light, overlay, red, green, blue, alpha);
    }

    private static boolean writeBox(ObjectList<LionfishModelRenderUtils.ModelBox> cubes, PoseStack.Pose pose, SodiumBufferBuilder fast,
                                    int light, int overlay, float red, float green, float blue, float alpha) {
        if (cubes == null) return false;
        int n = cubes.size();
        MemoryStack stack = MemoryStack.stackPush();
        try {
            int quads = 0;
            for (int c = 0; c < n; c++) {
                LionfishModelRenderUtils.ModelBox cube = cubes.get(c);
                Object[] list = cube == null ? null : LionfishFields.quads(cube);
                if (list == null) return false;
                quads += list.length;
            }
            if (quads == 0 || (long) quads * QUAD_BYTES + 64 > stack.getPointer()) return false;
            Matrix4f matrix = pose.m_252922_();
            Matrix3f normalMatrix = pose.m_252943_();
            int color = ColorABGR.pack(red, green, blue, alpha);
            long buffer = stack.nmalloc(quads * QUAD_BYTES);
            long ptr = buffer;
            for (int c = 0; c < n; c++) {
                for (Object quad : LionfishFields.quads(cubes.get(c))) {
                    // what Lionfish's loop would fail on: nothing is pushed and its loop runs (and fails) as before
                    Vector3f quadNormal = quad == null ? null : LionfishFields.normal(quad);
                    Object[] corners = quad == null ? null : LionfishFields.corners(quad);
                    if (quadNormal == null || corners == null || corners.length < 4) return false;
                    // Lionfish's arithmetic: the quad normal through the normal matrix, each corner / 16 through the pose
                    NORMAL.set(quadNormal).mul(normalMatrix);
                    int normal = NormI8.pack(NORMAL.x(), NORMAL.y(), NORMAL.z());
                    for (int k = 0; k < 4; k++) {
                        Object corner = corners[k];
                        Vector3f position = corner == null ? null : LionfishFields.position(corner);
                        if (position == null) return false;
                        POSITION.set(position.x() / 16.0F, position.y() / 16.0F, position.z() / 16.0F, 1.0F).mul(matrix);
                        ModelVertex.write(ptr, POSITION.x(), POSITION.y(), POSITION.z(), color,
                                LionfishFields.textureU(corner), LionfishFields.textureV(corner), overlay, light, normal);
                        ptr += ModelVertex.STRIDE;
                    }
                }
            }
            fast.push(stack, buffer, quads * 4, ModelVertex.FORMAT);
            return true;
        } catch (Throwable unexpected) {
            return false;                                   // nothing was pushed: Lionfish's loop runs
        } finally {
            stack.pop();
        }
    }
}
