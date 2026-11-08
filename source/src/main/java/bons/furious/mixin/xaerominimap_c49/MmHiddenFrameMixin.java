package bons.furious.mixin.xaerominimap_c49;

import bons.furious.patch.xaerominimap_c49.RadarPacing;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * xaerominimap_hidden_radar_skip (Xaero's Minimap 26.6.0 for NeoForge 1.21.1, client, since 1.0.49; change, off by default):
 * GameRenderer.render(DeltaTracker, boolean) (once per frame from Minecraft.runTick; the HUD layers, the minimap among them,
 * are drawn inside it) starts a frame, so a client tick knows whether the minimap was drawn in the frame before it
 * (RadarPacing). Minecraft code is not changed.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class MmHiddenFrameMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void bons$xaerominimapC49Frame(CallbackInfo ci) {
        RadarPacing.frame();
    }
}
