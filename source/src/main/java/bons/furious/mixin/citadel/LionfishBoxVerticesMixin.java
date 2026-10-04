package bons.furious.mixin.citadel;

import bons.furious.patch.citadel.ModelBoxVertices;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.client.model.tools.BasicModelPart;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * lionfishapi_model_vertices (LionfishAPI 3.1 for 1.21.1, the model library of L_Ender's Cataclysm; client): LionfishAPI
 * carries the same box loop as Citadel in AdvancedModelBox.doRender and BasicModelPart.doRender; the same two vector
 * constructions are redirected to ModelBoxVertices. Ported to 1.21.1: doRender is (Pose, VertexConsumer, light, overlay,
 * packed ARGB colour); the redirected constructions are unchanged. LionfishAPI is LGPL; no LionfishAPI code is carried.
 */
@Mixin(value = {AdvancedModelBox.class, BasicModelPart.class}, remap = false)
public abstract class LionfishBoxVerticesMixin {
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
