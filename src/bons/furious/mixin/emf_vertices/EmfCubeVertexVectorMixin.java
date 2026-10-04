package bons.furious.mixin.emf_vertices;

import bons.furious.patch.emf_vertices.CubeVertexVector;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * emf_cube_vertex_vector (Entity Model Features 3.2.4 for Minecraft 1.20.1 Forge, client only).
 *
 * EMFModelPartCustom$EMFCube.compile (m_171332_, EMF's override of ModelPart$Cube.compile) creates a Vector4f for every
 * vertex of every custom model cube it renders, which the JIT does not remove in play (14.9% of the render thread's
 * allocation with many entities on screen). This @Redirect of that NEW hands back CubeVertexVector's reused render-thread
 * vector holding the same four floats; EMF's own Matrix4f.transform call then produces the same values (see
 * CubeVertexVector). The polygon normal's Vector3f is left alone (the JIT already removes it: no Vector3f in the
 * recording). No other mod has a mixin on EMFCube. EMF is LGPL-3.0; only our redirect is added, no EMF code is carried.
 */
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartCustom$EMFCube", remap = false)
public abstract class EmfCubeVertexVectorMixin {
    @Redirect(method = "m_171332_", at = @At(value = "NEW", target = "(FFFF)Lorg/joml/Vector4f;"))
    private Vector4f bons$vertexVector(float x, float y, float z, float w) {
        return CubeVertexVector.of(x, y, z, w);
    }
}
