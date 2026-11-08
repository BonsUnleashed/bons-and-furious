package bons.furious.mixin.xaeroworldmap_gpu_c49;

import bons.furious.patch.xaeroworldmap_gpu_c49.UploadTimer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.graphics.TextureUploader;

/**
 * xaeroworldmap_upload_benchmark_async (Xaero's World Map 1.47.0 for NeoForge 1.21.1, client, since 1.0.49; change, off
 * by default). TextureUploader.uploadTextures: at its head the benchmark results that are ready are handed back
 * (UploadTimer.poll), and its glFinish before the first timed upload of a batch is left out. No World Map code is carried
 * (All Rights Reserved).
 */
@Mixin(value = TextureUploader.class, remap = false)
public abstract class WmUploadFinishMixin {
    @Inject(method = "uploadTextures", at = @At("HEAD"))
    private void bons$xaeroworldmapGpuC49Poll(CallbackInfo ci) {
        if (UploadTimer.active()) UploadTimer.poll();
    }

    @WrapOperation(method = "uploadTextures", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFinish()V"))
    private void bons$xaeroworldmapGpuC49Drain(Operation<Void> original) {
        if (UploadTimer.active()) {
            UploadTimer.finishes++;
            return;
        }
        original.call();
    }
}
