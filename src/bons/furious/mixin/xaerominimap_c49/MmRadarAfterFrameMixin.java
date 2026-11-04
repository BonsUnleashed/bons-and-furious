package bons.furious.mixin.xaerominimap_c49;

import bons.furious.patch.xaerominimap_c49.RadarPacing;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * xaerominimap_radar_after_frame (Xaero's Minimap 26.6.0, client, since 1.0.49; change, off by default): in Minecraft.runTick
 * (m_91383_), right after Window.updateDisplay (m_85435_, the frame is shown) and before the frame rate limit's wait, runs the
 * radar update the client tick kept (RadarPacing). Minecraft code is not changed.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MmRadarAfterFrameMixin {
    @Inject(method = "m_91383_", require = 1, allow = 1,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;m_85435_()V", shift = At.Shift.AFTER))
    private void bons$xaerominimapC49AfterFrame(boolean renderLevel, CallbackInfo ci) {
        RadarPacing.afterFrame();
    }
}
