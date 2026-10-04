package bons.furious.mixin.geckolib;

import bons.furious.patch.geckolib.QuadVertices;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * geckolib_quad_vectors (GeckoLib 4.8.4, client).
 *
 * GeoRenderer.createVerticesOfQuad (an interface default method; Mixin 0.8.5 allows only @Overwrite in interface
 * mixins) allocated a Vector4f per vertex. QuadVertices.emit transforms each vertex in one reused vector on the render
 * thread with the same JOML call and allocates as before elsewhere; see QuadVertices for why the vertex data is
 * identical. Renderers that override createVerticesOfQuad keep their own code. GeckoLib is MIT licensed.
 */
@Mixin(value = GeoRenderer.class, remap = false)
public interface GeoRendererQuadMixin<T extends GeoAnimatable> {
    /**
     * @author BonsUnleashed
     * @reason Transform the quad's vertices in a reused vector instead of a new Vector4f per vertex.
     */
    @Overwrite
    default void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight,
                                      int packedOverlay, float red, float green, float blue, float alpha) {
        QuadVertices.emit(quad, poseState, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
