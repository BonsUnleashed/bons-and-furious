package bons.furious.mixin.citadel;

import bons.furious.patch.citadel.ModelBoxVertices;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * citadel_model_vertices (Citadel 2.7.1 for NeoForge 1.21.1, client): AdvancedModelBox.doRender and
 * BasicModelPart.doRender (the same loop in both) keep their own iteration over boxes, quads and vertices and their own
 * vertex calls; only the per-quad normal vector and the per-vertex position vector are redirected to ModelBoxVertices,
 * which hands back one reused vector on the render thread (see there for why every vertex written is the one Citadel
 * writes). Ported to 1.21.1: doRender is (Pose, VertexConsumer, light, overlay, packed ARGB colour) and the normal is
 * normalised in place after the matrix multiply; the redirected constructions are unchanged. Citadel is LGPL-3.0; no
 * Citadel code is carried.
 */
@Mixin(value = {AdvancedModelBox.class, BasicModelPart.class}, remap = false)
public abstract class CitadelBoxVerticesMixin {
    @Redirect(method = "doRender(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "NEW", target = "(Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;"))
    private Vector3f bons$quadNormal(Vector3fc normal) {
        return ModelBoxVertices.normal(normal);
    }

    @Redirect(method = "doRender(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "NEW", target = "(FFFF)Lorg/joml/Vector4f;"))
    private Vector4f bons$vertexPosition(float x, float y, float z, float w) {
        return ModelBoxVertices.position(x, y, z, w);
    }
}
