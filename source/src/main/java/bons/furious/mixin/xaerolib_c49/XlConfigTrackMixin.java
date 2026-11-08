package bons.furious.mixin.xaerolib_c49;

import bons.furious.patch.xaerolib_c49.XaeroLibEpoch;
import bons.furious.patch.xaerolib_c49.XaeroLibTracked;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.lib.common.config.Config;

/**
 * xaerolib_current_profile_memo (XaeroLib 1.7.3 for NeoForge 1.21.1, client, since 1.0.49): change tracking on XaeroLib's
 * Config for XaeroLibEpoch. Config.set reports right before its first change (the failed-value map's remove, after its two
 * argument checks) and at each normal return; reset and addFailedSerializedValue at their head and return. A redirector
 * manager's cache config is quiet (marked by XlRedirectorTrackMixin) and reports nothing. No XaeroLib code is carried (All
 * Rights Reserved); the injections only count.
 */
@Mixin(value = Config.class, remap = false)
public abstract class XlConfigTrackMixin implements XaeroLibTracked {
    @Unique
    private boolean bons$xaerolibC49QuietFlag;

    @Override
    public void bons$xaerolibC49MarkQuiet() {
        this.bons$xaerolibC49QuietFlag = true;
    }

    @Override
    public boolean bons$xaerolibC49Quiet() {
        return this.bons$xaerolibC49QuietFlag;
    }

    @WrapOperation(method = "set(Lxaero/lib/common/config/option/ConfigOption;Ljava/lang/Object;)Ljava/lang/Object;",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private Object bons$xaerolibC49SetEnter(Map<?, ?> map, Object key, Operation<Object> original) {
        if (!this.bons$xaerolibC49QuietFlag) XaeroLibEpoch.enter();
        return original.call(map, key);
    }

    @ModifyReturnValue(method = "set(Lxaero/lib/common/config/option/ConfigOption;Ljava/lang/Object;)Ljava/lang/Object;", at = @At("RETURN"))
    private Object bons$xaerolibC49SetExit(Object original) {
        if (!this.bons$xaerolibC49QuietFlag) XaeroLibEpoch.exit();
        return original;
    }

    @Inject(method = "reset()V", at = @At("HEAD"))
    private void bons$xaerolibC49ResetEnter(CallbackInfo ci) {
        if (!this.bons$xaerolibC49QuietFlag) XaeroLibEpoch.enter();
    }

    @Inject(method = "reset()V", at = @At("RETURN"))
    private void bons$xaerolibC49ResetExit(CallbackInfo ci) {
        if (!this.bons$xaerolibC49QuietFlag) XaeroLibEpoch.exit();
    }

    @Inject(method = "addFailedSerializedValue(Lxaero/lib/common/config/option/ConfigOption;Ljava/lang/String;)V", at = @At("HEAD"))
    private void bons$xaerolibC49FailedEnter(CallbackInfo ci) {
        if (!this.bons$xaerolibC49QuietFlag) XaeroLibEpoch.enter();
    }

    @Inject(method = "addFailedSerializedValue(Lxaero/lib/common/config/option/ConfigOption;Ljava/lang/String;)V", at = @At("RETURN"))
    private void bons$xaerolibC49FailedExit(CallbackInfo ci) {
        if (!this.bons$xaerolibC49QuietFlag) XaeroLibEpoch.exit();
    }
}
