package bons.furious.mixin.vanilla_camera;

import bons.furious.patch.vanilla_camera.CameraFluidMemo;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_camera_fluid_memo (Minecraft 1.20.1 client), part 2 of 2: GameRenderer.render (m_109093_, once per render pass)
 * opens and closes the pass in which CameraFluidMemo may answer repeats. Its own hooks, independent of 1.0.23's
 * GameRendererFrameMixin (vanilla_fog_color_sample_memo), so either switch works without the other.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class CameraFluidFrameMixin {
    @Inject(method = "m_109093_", at = @At("HEAD"))
    private void bons$cameraFluidPassStart(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        CameraFluidMemo.beginFrame();
    }

    @Inject(method = "m_109093_", at = @At("RETURN"))
    private void bons$cameraFluidPassEnd(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        CameraFluidMemo.endFrame();
    }
}
