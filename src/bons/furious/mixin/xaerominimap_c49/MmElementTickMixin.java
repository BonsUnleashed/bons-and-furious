package bons.furious.mixin.xaerominimap_c49;

import bons.furious.patch.xaerominimap_c49.EmptyElementSkip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.MinimapProcessor;

/**
 * xaerominimap_empty_element_renderer_skip (Xaero's Minimap 26.6.0, client, since 1.0.49; change, off by default): the start
 * of MinimapProcessor.onClientTick counts the client ticks the element renderers' first-frame counts belong to
 * (EmptyElementSkip). No Minimap code is carried (All Rights Reserved).
 */
@Mixin(value = MinimapProcessor.class, remap = false)
public abstract class MmElementTickMixin {
    @Inject(method = "onClientTick", require = 1, allow = 1, at = @At("HEAD"))
    private void bons$xaerominimapC49ElementTick(CallbackInfo ci) {
        EmptyElementSkip.tick();
    }
}
