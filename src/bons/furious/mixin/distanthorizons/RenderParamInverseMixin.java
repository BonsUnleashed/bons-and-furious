package bons.furious.mixin.distanthorizons;

import bons.furious.patch.distanthorizons.MatrixMemo;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_render_param_inverse_reuse (Distant Horizons 3.3.2, client).
 *
 * GlDhTerrainShaderProgram updates one shared DhApiBeforeBufferRenderEvent parameter object for every LOD buffer it
 * draws, and each update inverts two 4x4 matrices and multiplies two more, although the inputs are the same for every
 * buffer of a render pass. The two invert() calls and the multiply() call of DhApiRenderParam.update now go through a
 * per-object memo that copies the previous result when the inputs are bit-identical (see MatrixMemo). Every field of
 * the parameter object still gets exactly the value the original update computes.
 */
@Mixin(value = DhApiRenderParam.class, remap = false)
public abstract class RenderParamInverseMixin {
    @Unique
    private final MatrixMemo bons$projectionInverse = new MatrixMemo();

    @Unique
    private final MatrixMemo bons$viewProjection = new MatrixMemo();

    @Unique
    private final MatrixMemo bons$viewProjectionInverse = new MatrixMemo();

    @Redirect(method = "update(Lcom/seibel/distanthorizons/api/enums/rendering/EDhApiRenderPass;FFFLcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;ILcom/seibel/distanthorizons/api/interfaces/world/IDhApiLevelWrapper;)V",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;invert()V", ordinal = 0))
    private void bons$invertProjection(DhApiMat4f matrix) {
        this.bons$projectionInverse.invert(matrix);
    }

    @Redirect(method = "update(Lcom/seibel/distanthorizons/api/enums/rendering/EDhApiRenderPass;FFFLcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;ILcom/seibel/distanthorizons/api/interfaces/world/IDhApiLevelWrapper;)V",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;multiply(Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;)V"))
    private void bons$multiplyViewProjection(DhApiMat4f left, DhApiMat4f right) {
        this.bons$viewProjection.multiply(left, right);
    }

    @Redirect(method = "update(Lcom/seibel/distanthorizons/api/enums/rendering/EDhApiRenderPass;FFFLcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;ILcom/seibel/distanthorizons/api/interfaces/world/IDhApiLevelWrapper;)V",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/api/objects/math/DhApiMat4f;invert()V", ordinal = 1))
    private void bons$invertViewProjection(DhApiMat4f matrix) {
        this.bons$viewProjectionInverse.invert(matrix);
    }
}
