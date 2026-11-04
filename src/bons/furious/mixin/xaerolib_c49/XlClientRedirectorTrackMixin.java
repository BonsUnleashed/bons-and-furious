package bons.furious.mixin.xaerolib_c49;

import bons.furious.patch.xaerolib_c49.XaeroLibEpoch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.lib.client.config.option.value.redirect.ClientOptionValueRedirectorManager;

/**
 * xaerolib_current_profile_memo (XaeroLib 1.7.3, client, since 1.0.49): change tracking on the client redirector manager's
 * six-argument register (the one Xaero's Minimap and World Map call; the three-argument one throws) for XaeroLibEpoch: it
 * reports right before its map write (after the duplicate check) and at its return. No XaeroLib code is carried.
 */
@Mixin(value = ClientOptionValueRedirectorManager.class, remap = false)
public abstract class XlClientRedirectorTrackMixin {
    @Inject(method = "register(Lxaero/lib/common/config/option/ConfigOption;Ljava/util/function/Supplier;Ljava/util/function/Predicate;Ljava/util/function/Function;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private void bons$xaerolibC49RegisterEnter(CallbackInfo ci) {
        XaeroLibEpoch.enter();
    }

    @Inject(method = "register(Lxaero/lib/common/config/option/ConfigOption;Ljava/util/function/Supplier;Ljava/util/function/Predicate;Ljava/util/function/Function;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;)V",
            at = @At("RETURN"))
    private void bons$xaerolibC49RegisterExit(CallbackInfo ci) {
        XaeroLibEpoch.exit();
    }
}
