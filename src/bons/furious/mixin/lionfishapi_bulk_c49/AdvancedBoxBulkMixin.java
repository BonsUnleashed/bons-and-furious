package bons.furious.mixin.lionfishapi_bulk_c49;

import bons.furious.patch.lionfishapi_bulk_c49.BulkVertices;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * lionfishapi_model_bulk_vertices (Lionfish API 2.8 with Embeddium 0.3.31; client; a trade-off, off by default: faster
 * without a shader pack, a few per cent slower per box with one): AdvancedModelBox.render calls doRender
 * once for the box's own cubes. When BulkVertices can write the box in one block into Embeddium's fast consumer (byte for
 * byte what doRender's loop writes), that call is skipped; otherwise doRender runs as shipped (with the vector reuse of
 * lionfishapi_model_vertices, whose redirects sit inside its loop). doRender itself is not changed: where the switch stands
 * down (any other consumer, such as the plain BufferBuilder Oculus' batched rendering hands out with a shader pack), the
 * JIT compiles the same doRender as without the switch, and the only added work is the consumer's class test in render.
 * Our own logic only; Lionfish API is LGPL and none of its code is carried.
 */
@Mixin(value = AdvancedModelBox.class, remap = false)
public abstract class AdvancedBoxBulkMixin {
    @WrapWithCondition(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/L_Ender/lionfishapi/client/model/tools/AdvancedModelBox;doRender(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
            require = 1, allow = 1)
    private boolean bons$lionfishapi_bulk_c49Render(AdvancedModelBox box, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
                                                    float red, float green, float blue, float alpha) {
        // any consumer but Embeddium's fast one (null class without Embeddium): doRender as shipped, no call made
        if (consumer == null || consumer.getClass() != BulkVertices.FAST) return true;
        return !BulkVertices.render(box, pose, consumer, light, overlay, red, green, blue, alpha);
    }
}
