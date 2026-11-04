package bons.furious.mixin.xaerolib_c49;

import bons.furious.patch.xaerolib_c49.XaeroLibEpoch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.lib.common.config.single.SingleConfigManager;

/**
 * xaerolib_current_profile_memo (XaeroLib 1.7.3, client, since 1.0.49): change tracking on SingleConfigManager for
 * XaeroLibEpoch: setConfig (the config object every read of the manager goes to) reports at its head and return,
 * setRedirectorManager right before its field write (after its "already set" check) and at its return. No XaeroLib code is
 * carried; the injections only count.
 */
@Mixin(value = SingleConfigManager.class, remap = false)
public abstract class XlManagersTrackMixin {
    @Inject(method = "setConfig", at = @At("HEAD"))
    private void bons$xaerolibC49SetConfigEnter(CallbackInfo ci) {
        XaeroLibEpoch.enter();
    }

    @Inject(method = "setConfig", at = @At("RETURN"))
    private void bons$xaerolibC49SetConfigExit(CallbackInfo ci) {
        XaeroLibEpoch.exit();
    }

    @Inject(method = "setRedirectorManager", at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.PUTFIELD,
            target = "Lxaero/lib/common/config/single/SingleConfigManager;redirectorManager:Lxaero/lib/common/config/option/value/redirect/OptionValueRedirectorManager;"))
    private void bons$xaerolibC49SetRedirectorsEnter(CallbackInfo ci) {
        XaeroLibEpoch.enter();
    }

    @Inject(method = "setRedirectorManager", at = @At("RETURN"))
    private void bons$xaerolibC49SetRedirectorsExit(CallbackInfo ci) {
        XaeroLibEpoch.exit();
    }
}
