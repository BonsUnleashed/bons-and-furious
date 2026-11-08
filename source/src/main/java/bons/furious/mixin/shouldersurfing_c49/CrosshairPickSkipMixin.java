package bons.furious.mixin.shouldersurfing_c49;

import bons.furious.patch.shouldersurfing_c49.CrosshairPickSkip;
import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.math.Vec2f;
import com.github.exopandora.shouldersurfing.client.renderer.CrosshairRenderer;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * shouldersurfing_idle_crosshair_pick_skip (Shoulder Surfing Reloaded 1.21.1-5.3.0; MIT; client side): renderTick's call
 * of CrosshairRenderer.computeCrosshairOffset (its only caller) answers null without the two object picks while shoulder
 * surfing with a static crosshair and no showable obstruction indicator, the states in which nothing reads the offset (see
 * CrosshairPickSkip); otherwise the same method is called with the same arguments. computeCrosshairOffset itself is left
 * as it ships, so the path that still picks runs the original code.
 */
@Mixin(value = CrosshairRenderer.class, remap = false)
public abstract class CrosshairPickSkipMixin {
    @Shadow
    private static Vec2f computeCrosshairOffset(IShoulderSurfing instance, Camera camera, Matrix4f modelView, Matrix4f projection,
                                                boolean isCrosshairDynamic, float partialTick) {
        throw new AssertionError();
    }

    @Redirect(method = "renderTick(Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;F)V",
            at = @At(value = "INVOKE", target = "Lcom/github/exopandora/shouldersurfing/client/renderer/CrosshairRenderer;computeCrosshairOffset(Lcom/github/exopandora/shouldersurfing/api/client/IShoulderSurfing;Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ZF)Lcom/github/exopandora/shouldersurfing/api/math/Vec2f;"),
            require = 1, allow = 1)
    private Vec2f bons$shouldersurfingC49SkipIdlePicks(IShoulderSurfing instance, Camera camera, Matrix4f modelView, Matrix4f projection,
                                                       boolean isCrosshairDynamic, float partialTick) {
        return CrosshairPickSkip.skip(instance, isCrosshairDynamic) ? null
                : computeCrosshairOffset(instance, camera, modelView, projection, isCrosshairDynamic, partialTick);
    }
}
