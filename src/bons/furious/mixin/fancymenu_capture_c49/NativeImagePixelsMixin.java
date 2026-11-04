package bons.furious.mixin.fancymenu_capture_c49;

import bons.furious.patch.fancymenu_capture_c49.SeamlessCapture;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * fancymenu_seamless_capture_async (client): gives NativeImage the read-only pixel-address getter the asynchronous capture
 * copies into (SeamlessCapture.NativeImagePixels); nothing else of NativeImage changes.
 */
@Mixin(value = NativeImage.class, remap = false)
public abstract class NativeImagePixelsMixin implements SeamlessCapture.NativeImagePixels {
    @Shadow
    private long f_84964_;

    @Override
    public long bons$fancymenuCaptureC49Pixels() {
        return this.f_84964_;
    }
}
