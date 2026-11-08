package bons.furious.mixin.touchnmotion_c49;

import bons.furious.patch.touchnmotion_c49.TnmFrame;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * touchnmotion_ground_probe_direct (Touch'n Motion 1.0.4 for NeoForge 1.21.1, client, since 1.0.49), the frame:
 * GameRenderer.render(DeltaTracker, boolean) (once per frame, from Minecraft.runTick after the queued tasks and client
 * ticks) opens and closes the render frame in which Touch'n Motion's ground probes may walk directly (TnmFrame). Minecraft
 * code is not changed.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class TnmProbeFrameMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void bons$touchnmotionC49FrameOpen(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        TnmFrame.open();
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("RETURN"))
    private void bons$touchnmotionC49FrameClose(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        TnmFrame.close();
    }
}
