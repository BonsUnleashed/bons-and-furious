package bons.furious.patch.geckolib;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * Bons and Furious switch geckolib_quad_vectors (GeckoLib 4.8.4, client).
 *
 * GeoRenderer.createVerticesOfQuad - the default method every GeckoLib entity, block, item and armour renderer uses -
 * transformed each vertex with poseState.transform(new Vector4f(x, y, z, 1)). In play that vector is not removed by
 * the JIT (review-8 JFR: 69 MB of org.joml.Vector4f in 38 s under this method, 1.06% of the render thread's allocation,
 * 0.86% of its time): the vertex call behind it reaches a dozen VertexConsumer types and the transform is not inlined.
 *
 * On the render thread the vertex is now transformed in one reused Vector4f: set(x, y, z, 1) and the same
 * Matrix4f.transform call, so the same JOML arithmetic produces the same three floats, which are read into the vertex
 * call's arguments before that call runs (a consumer that renders again re-enters with the vector already read). Other
 * threads (some mods draw GeckoLib items off the render thread) allocate as GeckoLib does. The quad's normal, which the
 * caller transformed, is passed through untouched.
 *
 * -Dbons_and_furious.geckolibQuadVectors=false restores GeckoLib's own body at runtime.
 */
public final class QuadVertices {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** geckolib_quad_vectors: -Dbons_and_furious.geckolibQuadVectors=false allocates a vector per vertex as GeckoLib does. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.geckolibQuadVectors", "true"));
    private static final Vector4f SCRATCH = new Vector4f();
    private static volatile boolean announced;

    private QuadVertices() {}

    public static void emit(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight, int packedOverlay,
                            float red, float green, float blue, float alpha) {
        if (!enabled || !RenderSystem.isOnRenderThread()) {
            for (GeoVertex vertex : quad.vertices()) {
                Vector3f position = vertex.position();
                Vector4f vector4f = poseState.transform(new Vector4f(position.x(), position.y(), position.z(), 1.0f));
                buffer.m_5954_(vector4f.x(), vector4f.y(), vector4f.z(), red, green, blue, alpha, vertex.texU(), vertex.texV(),
                        packedOverlay, packedLight, normal.x(), normal.y(), normal.z());
            }
            return;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: geckolib_quad_vectors applies (GeckoLib vertices are transformed in a reused vector)");
        }
        Vector4f scratch = SCRATCH;
        for (GeoVertex vertex : quad.vertices()) {
            Vector3f position = vertex.position();
            Vector4f vector4f = poseState.transform(scratch.set(position.x(), position.y(), position.z(), 1.0f));
            buffer.m_5954_(vector4f.x(), vector4f.y(), vector4f.z(), red, green, blue, alpha, vertex.texU(), vertex.texV(),
                    packedOverlay, packedLight, normal.x(), normal.y(), normal.z());
        }
    }
}
