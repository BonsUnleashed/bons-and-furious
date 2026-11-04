package bons.furious.mixin.xaerominimap_c49;

import bons.furious.patch.xaerominimap_c49.RadarPacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.render.MinimapRenderer;

/**
 * xaerominimap_hidden_radar_skip (Xaero's Minimap 26.6.0, client, since 1.0.49; change, off by default): at the start of
 * MinimapRenderer.renderMinimap (the framebuffer and the safe-mode minimap both draw through it) marks the minimap drawn in
 * this frame and, after updates were left out, runs the radar update before the minimap draws (RadarPacing). No Minimap code
 * is carried (All Rights Reserved).
 */
@Mixin(value = MinimapRenderer.class, remap = false)
public abstract class MmMinimapDrawnMixin {
    @Inject(method = "renderMinimap", require = 1, allow = 1, at = @At("HEAD"))
    private void bons$xaerominimapC49Drawn(CallbackInfo ci) {
        RadarPacing.drawing();
    }
}
