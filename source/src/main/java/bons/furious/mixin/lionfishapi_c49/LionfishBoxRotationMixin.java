package bons.furious.mixin.lionfishapi_c49;

import bons.furious.patch.lionfishapi_c49.BoxRotations;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * lionfishapi_box_rotation_reuse (Lionfish API 3.1 for Minecraft 1.21.1, client), part 1 of 2: the three Axis.rotation(angle)
 * calls in AdvancedModelBox.translateAndRotate go through BoxRotations, which on the render thread fills one reused
 * quaternion with the same JOML rotation. Lionfish API is LGPL; no Lionfish code is carried.
 */
@Mixin(value = AdvancedModelBox.class, remap = false)
public abstract class LionfishBoxRotationMixin {
    @Redirect(method = "translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "INVOKE",
            target = "Lcom/mojang/math/Axis;rotation(F)Lorg/joml/Quaternionf;"), require = 3, allow = 3)
    private Quaternionf bons$reusedRotation(Axis axis, float angle) {
        return BoxRotations.rotation(axis, angle);
    }
}
