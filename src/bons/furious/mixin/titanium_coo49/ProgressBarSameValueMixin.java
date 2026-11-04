package bons.furious.mixin.titanium_coo49;

import bons.furious.patch.titanium_coo49.TitaniumProgress;
import com.hrznstudio.titanium.component.progress.ProgressBarComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * titanium_progress_same_value_skip (Titanium 3.8.35; kind change, off by default; no Titanium code is carried).
 * ProgressBarComponent.setProgress returns at once when the value equals the one the bar holds, so the machine is not
 * marked changed by a same-value write. See TitaniumProgress.
 */
@Mixin(value = ProgressBarComponent.class, remap = false)
public abstract class ProgressBarSameValueMixin {
    @Shadow
    private int progress;

    @Inject(method = "setProgress(I)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$titaniumProgressSameValue(int value, CallbackInfo ci) {
        if (TitaniumProgress.enabled && value == this.progress) {
            TitaniumProgress.skipped();
            ci.cancel();
        }
    }
}
