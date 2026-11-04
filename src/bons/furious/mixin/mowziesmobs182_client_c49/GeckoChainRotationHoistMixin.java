package bons.furious.mixin.mowziesmobs182_client_c49;

import bons.furious.patch.mowziesmobs182_client_c49.ChainHoist;
import com.bobmowzie.mowziesmobs.client.model.tools.dynamics.GeckoDynamicChain;
import com.bobmowzie.mowziesmobs.client.render.entity.MowzieGeoEntityRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mowziesmobs_chain_rotation_hoist (Mowzie's Mobs 1.8.2; client; since 1.0.49): the renderer's model render matrix is
 * worked out once per GeckoDynamicChain.setChainFromRenderPos call and handed to the later bones as copies (ChainHoist).
 * The 1.8.2 method only: on 1.7.3 the guard stands down. Our own logic only.
 */
@Mixin(value = GeckoDynamicChain.class, remap = false)
public abstract class GeckoChainRotationHoistMixin {
    private static final String TARGET = "setChainFromRenderPos([Lcom/bobmowzie/mowziesmobs/client/model/tools/geckolib/MowzieGeoBone;"
            + "[Lcom/bobmowzie/mowziesmobs/client/model/tools/geckolib/MowzieGeoBone;DLcom/bobmowzie/mowziesmobs/client/render/entity/MowzieGeoEntityRenderer;)V";

    @WrapOperation(method = TARGET,
            at = @At(value = "INVOKE", target = "Lcom/bobmowzie/mowziesmobs/client/render/entity/MowzieGeoEntityRenderer;getModelRenderMatrix()Lorg/joml/Matrix4f;", remap = false),
            require = 1, allow = 1, remap = false)
    private Matrix4f bons$m182RenderMatrix(MowzieGeoEntityRenderer<?> renderer, Operation<Matrix4f> original,
                                           @Share("bons$m182RenderMatrix") LocalRef<Matrix4f> kept) {
        return ChainHoist.renderMatrix(renderer, original, kept);
    }
}
