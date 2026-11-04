package bons.furious.mixin.vanilla_sky_c49;

import bons.furious.patch.vanilla_sky_c49.SkySample;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_sky_color_sample_memo (Minecraft 1.20.1 client), part 1 of 2: GameRenderer.render (m_109093_, once per frame)
 * opens and closes the frame in which SkySample may reuse a sky colour sample.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class SkyFrameMixin {
    @Inject(method = "m_109093_", at = @At("HEAD"))
    private void bons$beginSkyFrame(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        SkySample.beginFrame();
    }

    @Inject(method = "m_109093_", at = @At("RETURN"))
    private void bons$endSkyFrame(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        SkySample.endFrame();
    }
}