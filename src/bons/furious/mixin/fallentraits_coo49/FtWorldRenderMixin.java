package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtScale;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fallentraits_render_scale_frame_memo (client side; trade-off, off by default): @Inject at the head and at every return of
 * LevelRenderer.renderLevel (m_109599_) marks the thread that renders the world for FtScale (only reads and writes our own
 * mark; adds no behaviour to the render).
 */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class FtWorldRenderMixin {
    @Inject(method = "m_109599_(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At("HEAD"), require = 1, allow = 1)
    private void bons$fallentraitsCoo49WorldRenderStart(CallbackInfo ci) {
        FtScale.worldRenderStart();
    }

    @Inject(method = "m_109599_(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At("RETURN"), require = 1)
    private void bons$fallentraitsCoo49WorldRenderEnd(CallbackInfo ci) {
        FtScale.worldRenderEnd();
    }
}
