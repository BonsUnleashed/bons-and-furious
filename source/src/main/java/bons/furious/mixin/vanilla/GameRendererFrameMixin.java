package bons.furious.mixin.vanilla;

import bons.furious.patch.vanilla.FogSample;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_fog_color_sample_memo (Minecraft 1.20.1 client), part 2 of 2: GameRenderer.render (render, once per frame)
 * opens and closes the frame in which FogSample may reuse a sample.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class GameRendererFrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void bons$beginFrame(CallbackInfo ci) {
        FogSample.beginFrame();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void bons$endFrame(CallbackInfo ci) {
        FogSample.endFrame();
    }
}
