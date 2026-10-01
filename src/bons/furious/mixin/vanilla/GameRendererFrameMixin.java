package bons.furious.mixin.vanilla;

import bons.furious.patch.vanilla.FogSample;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_fog_color_sample_memo (Minecraft 1.20.1 client), part 2 of 2: GameRenderer.render (m_109093_, once per frame)
 * opens and closes the frame in which FogSample may reuse a sample.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class GameRendererFrameMixin {
    @Inject(method = "m_109093_", at = @At("HEAD"))
    private void bons$beginFrame(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        FogSample.beginFrame();
    }

    @Inject(method = "m_109093_", at = @At("RETURN"))
    private void bons$endFrame(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        FogSample.endFrame();
    }
}
