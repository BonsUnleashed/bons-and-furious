package bons.furious.mixin.fancymenu_capture_c49;

import bons.furious.patch.fancymenu_capture_c49.SeamlessCapture;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import de.keksuccino.fancymenu.customization.global.SeamlessWorldLoadingHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fancymenu_seamless_capture_async (FancyMenu 3.9.14, DSMSL licence: injectors with our own logic only; client; exact
 * variant). In SeamlessWorldLoadingHandler: the capture call (Screenshot.takeScreenshot in captureFrameIfNeeded) starts an
 * asynchronous read and hands FancyMenu a placeholder (@WrapOperation); the one-per-second check's read of
 * lastCapturedImage (@ModifyExpressionValue) puts the finished image in place of the placeholder; saveAndClearCapture
 * finishes a pending capture first (@Inject HEAD) and clearLastCapturedImage drops one (@Inject HEAD, cancellable, only
 * while the placeholder is held, so the placeholder itself is never closed). See SeamlessCapture.
 */
@Mixin(value = SeamlessWorldLoadingHandler.class, remap = false)
public abstract class SeamlessCaptureMixin {
    @Shadow
    private static NativeImage lastCapturedImage;

    @WrapOperation(method = "captureFrameIfNeeded(Lcom/mojang/blaze3d/pipeline/RenderTarget;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;m_92279_(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;"),
            require = 1, allow = 1)
    private static NativeImage bons$fancymenuCaptureC49Take(RenderTarget target, Operation<NativeImage> original) {
        if (SeamlessCapture.isMarker(lastCapturedImage)) lastCapturedImage = SeamlessCapture.finishNow();
        return SeamlessCapture.capture(target, original);
    }

    @ModifyExpressionValue(method = "captureFrameIfNeeded(Lcom/mojang/blaze3d/pipeline/RenderTarget;)V",
            at = @At(value = "FIELD", target = "Lde/keksuccino/fancymenu/customization/global/SeamlessWorldLoadingHandler;lastCapturedImage:Lcom/mojang/blaze3d/platform/NativeImage;", opcode = Opcodes.GETSTATIC),
            require = 1, allow = 1)
    private static NativeImage bons$fancymenuCaptureC49Poll(NativeImage current) {
        if (SeamlessCapture.isMarker(current)) {
            NativeImage done = SeamlessCapture.poll();
            if (done != null) {
                lastCapturedImage = done;
                return done;
            }
        }
        return current;
    }

    @Inject(method = "saveAndClearCapture", at = @At("HEAD"), require = 1, allow = 1)
    private static void bons$fancymenuCaptureC49Save(CallbackInfo ci) {
        if (SeamlessCapture.isMarker(lastCapturedImage)) lastCapturedImage = SeamlessCapture.finishNow();
    }

    @Inject(method = "clearLastCapturedImage()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$fancymenuCaptureC49Clear(CallbackInfo ci) {
        if (SeamlessCapture.isMarker(lastCapturedImage)) {
            SeamlessCapture.drop();
            lastCapturedImage = null;
            ci.cancel();
        }
    }
}
