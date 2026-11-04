package bons.furious.mixin.xaerolib_c49;

import bons.furious.patch.xaerolib_c49.XaeroLibEpoch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.lib.common.config.profile.ConfigProfileManager;

/**
 * xaerolib_current_profile_memo (XaeroLib 1.7.3, client, since 1.0.49): change tracking on ConfigProfileManager (the
 * id -> profile map getCurrentProfile reads) for XaeroLibEpoch: add(int, profile) reports right before its map write (after
 * its duplicate-id check; the one-argument add calls it), remove(id) right before its map removal (after its unknown-id
 * check), reset at its head; each at its return. No XaeroLib code is carried; the injections only count.
 */
@Mixin(value = ConfigProfileManager.class, remap = false)
public abstract class XlProfilesTrackMixin {
    @Inject(method = "add(ILxaero/lib/common/config/profile/ConfigProfile;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private void bons$xaerolibC49AddEnter(CallbackInfo ci) {
        XaeroLibEpoch.enter();
    }

    @Inject(method = "add(ILxaero/lib/common/config/profile/ConfigProfile;)V", at = @At("RETURN"))
    private void bons$xaerolibC49AddExit(CallbackInfo ci) {
        XaeroLibEpoch.exit();
    }

    @Inject(method = "remove", at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private void bons$xaerolibC49RemoveEnter(CallbackInfo ci) {
        XaeroLibEpoch.enter();
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void bons$xaerolibC49RemoveExit(CallbackInfo ci) {
        XaeroLibEpoch.exit();
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void bons$xaerolibC49ResetEnter(CallbackInfo ci) {
        XaeroLibEpoch.enter();
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void bons$xaerolibC49ResetExit(CallbackInfo ci) {
        XaeroLibEpoch.exit();
    }
}
