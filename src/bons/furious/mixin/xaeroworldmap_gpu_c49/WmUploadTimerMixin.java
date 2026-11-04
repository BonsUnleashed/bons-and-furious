package bons.furious.mixin.xaeroworldmap_gpu_c49;

import bons.furious.patch.xaeroworldmap_gpu_c49.UploadTimer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.graphics.TextureUploadBenchmark;

/**
 * xaeroworldmap_upload_benchmark_async (Xaero's World Map 1.47.0, client, since 1.0.49; change, off by default).
 * TextureUploadBenchmark: pre() writes a GPU timestamp instead of starting the CPU timer, post(type) writes the second one
 * and returns; when UploadTimer hands the result back, post(type) runs as shipped without its glFinish and with the GPU time
 * as the timer's result. See UploadTimer. No World Map code is carried (All Rights Reserved).
 */
@Mixin(value = TextureUploadBenchmark.class, remap = false)
public abstract class WmUploadTimerMixin {
    @Inject(method = "pre", at = @At("HEAD"), cancellable = true)
    private void bons$xaeroworldmapGpuC49Pre(CallbackInfo ci) {
        if (UploadTimer.active()) {
            UploadTimer.start();
            ci.cancel();
        }
    }

    @Inject(method = "post", at = @At("HEAD"), cancellable = true)
    private void bons$xaeroworldmapGpuC49Post(int type, CallbackInfo ci) {
        if (!UploadTimer.replaying() && UploadTimer.active()) {
            UploadTimer.end((TextureUploadBenchmark) (Object) this, type);
            ci.cancel();
        }
    }

    @WrapOperation(method = "post", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFinish()V"))
    private void bons$xaeroworldmapGpuC49PostFinish(Operation<Void> original) {
        if (UploadTimer.replaying()) {
            UploadTimer.finishes++;
            return;
        }
        original.call();
    }

    @WrapOperation(method = "post", at = @At(value = "INVOKE", target = "Lxaero/map/misc/Misc;timerResult()I"))
    private int bons$xaeroworldmapGpuC49PostTime(Operation<Integer> original) {
        return UploadTimer.replaying() ? UploadTimer.replayNanos() : original.call();
    }
}
