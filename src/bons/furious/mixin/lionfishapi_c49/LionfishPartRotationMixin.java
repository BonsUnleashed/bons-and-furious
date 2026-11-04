package bons.furious.mixin.lionfishapi_c49;

import bons.furious.patch.lionfishapi_c49.BoxRotations;
import com.github.L_Ender.lionfishapi.client.model.tools.BasicModelPart;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * lionfishapi_box_rotation_reuse (Lionfish API 2.8 and 3.0, client), part 2 of 2: the new Quaternionf() that
 * BasicModelPart.translateAndRotate fills with rotationZYX(z, y, x) is the reused render-thread quaternion of BoxRotations
 * (rotationZYX writes all four components). Lionfish API is LGPL; no Lionfish code is carried.
 */
@Mixin(value = BasicModelPart.class, remap = false)
public abstract class LionfishPartRotationMixin {
    @Redirect(method = "translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "NEW", target = "()Lorg/joml/Quaternionf;"),
            require = 1, allow = 1)
    private Quaternionf bons$reusedPartRotation() {
        return BoxRotations.fresh();
    }
}
