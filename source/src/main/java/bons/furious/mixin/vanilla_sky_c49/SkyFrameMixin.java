package bons.furious.mixin.vanilla_sky_c49;

import bons.furious.patch.vanilla_sky_c49.SkySample;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_sky_color_sample_memo (Minecraft 1.21.1, client), part 1 of 2: GameRenderer.render(DeltaTracker, boolean), called
 * once per frame from Minecraft.runTick after the queued tasks have run, opens and closes the frame in which SkySample may
 * hand out the frame's sky colour sample again. The method itself runs unchanged.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class SkyFrameMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void bons$beginSkyFrame(CallbackInfo ci) {
        SkySample.beginFrame();
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("RETURN"))
    private void bons$endSkyFrame(CallbackInfo ci) {
        SkySample.endFrame();
    }
}
