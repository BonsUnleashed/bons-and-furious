package bons.furious.mixin.touchnmotion_c49;

import bons.furious.patch.touchnmotion_c49.TnmFrame;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * touchnmotion_ground_probe_direct (Touch'n Motion 1.0.4, client, since 1.0.49), the frame: GameRenderer.render
 * (m_109093_, once per frame) opens and closes the render frame in which Touch'n Motion's ground probes may walk directly
 * (TnmFrame). Minecraft code is not changed.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class TnmProbeFrameMixin {
    @Inject(method = "m_109093_", at = @At("HEAD"))
    private void bons$touchnmotionC49FrameOpen(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        TnmFrame.open();
    }

    @Inject(method = "m_109093_", at = @At("RETURN"))
    private void bons$touchnmotionC49FrameClose(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        TnmFrame.close();
    }
}
