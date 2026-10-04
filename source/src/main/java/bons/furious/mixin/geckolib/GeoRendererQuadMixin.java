package bons.furious.mixin.geckolib;

import bons.furious.patch.geckolib.QuadVertices;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * geckolib_quad_vectors (GeckoLib 4.9.3 for NeoForge 1.21.1, client).
 *
 * GeoRenderer.createVerticesOfQuad (an interface default method; Mixin allows only @Overwrite in interface mixins)
 * allocated a Vector4f per vertex. QuadVertices.emit transforms each vertex in one reused vector on the render thread
 * with the same JOML call and allocates as before elsewhere; see QuadVertices for why the vertex data is identical.
 * Renderers that override createVerticesOfQuad (GeckoLib's own DynamicGeoEntityRenderer, DynamicGeoBlockRenderer and
 * DynamicGeoItemRenderer among them) keep their own code. Ported to 1.21.1: the method takes the packed ARGB colour (int)
 * in place of four floats and writes through the 1.21 VertexConsumer.addVertex bulk method. GeckoLib is MIT licensed.
 */
@Mixin(value = GeoRenderer.class, remap = false)
public interface GeoRendererQuadMixin<T extends GeoAnimatable> {
    /**
     * @author BonsUnleashed
     * @reason Transform the quad's vertices in a reused vector instead of a new Vector4f per vertex.
     */
    @Overwrite
    default void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight,
                                      int packedOverlay, int colour) {
        QuadVertices.emit(quad, poseState, normal, buffer, packedLight, packedOverlay, colour);
    }
}
