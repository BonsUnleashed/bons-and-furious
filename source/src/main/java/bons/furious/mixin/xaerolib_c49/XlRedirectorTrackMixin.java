package bons.furious.mixin.xaerolib_c49;

import bons.furious.patch.xaerolib_c49.XaeroLibEpoch;
import bons.furious.patch.xaerolib_c49.XaeroLibRedirectors;
import bons.furious.patch.xaerolib_c49.XaeroLibTracked;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.lib.common.config.Config;
import xaero.lib.common.config.option.value.redirect.OptionValueRedirectorManager;

/**
 * xaerolib_current_profile_memo (XaeroLib 1.7.3 for NeoForge 1.21.1, client, since 1.0.49): change tracking on
 * OptionValueRedirectorManager for XaeroLibEpoch. Its constructor marks its own cache config quiet (XaeroLib writes that
 * config on many reads of redirected options; neither memo reads it); register(option, supplier, condition) reports right
 * before its map write (after its frozen and duplicate checks) and at its return; and the manager answers whether a
 * redirector is registered for an option (a read of its map). No XaeroLib code is carried; the injections only count and
 * read.
 */
@Mixin(value = OptionValueRedirectorManager.class, remap = false)
public abstract class XlRedirectorTrackMixin implements XaeroLibRedirectors {
    @Shadow
    @Final
    protected Map<?, ?> redirectors;

    @Shadow
    @Final
    private Config cacheConfig;

    @Override
    public boolean bons$xaerolibC49HasRedirector(Object option) {
        return this.redirectors.get(option) != null;
    }

    @Inject(method = "<init>(Ljava/util/Map;Lxaero/lib/common/config/Config;)V", at = @At("RETURN"))
    private void bons$xaerolibC49QuietCache(CallbackInfo ci) {
        if (this.cacheConfig instanceof XaeroLibTracked t) t.bons$xaerolibC49MarkQuiet();
    }

    @Inject(method = "register(Lxaero/lib/common/config/option/ConfigOption;Ljava/util/function/Supplier;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private void bons$xaerolibC49RegisterEnter(CallbackInfo ci) {
        XaeroLibEpoch.enter();
    }

    @Inject(method = "register(Lxaero/lib/common/config/option/ConfigOption;Ljava/util/function/Supplier;Ljava/util/function/Predicate;)V", at = @At("RETURN"))
    private void bons$xaerolibC49RegisterExit(CallbackInfo ci) {
        XaeroLibEpoch.exit();
    }
}
