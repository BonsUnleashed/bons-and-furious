package bons.furious.mixin.xaerolib_c49;

import bons.furious.patch.xaerolib_c49.XaeroLibEpoch;
import bons.furious.patch.xaerolib_c49.XaeroLibMemos;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.Config;
import xaero.lib.common.config.profile.ConfigProfile;
import xaero.lib.common.config.single.SingleConfigManager;

/**
 * xaerolib_current_profile_memo (XaeroLib 1.7.3, client, since 1.0.49; exact). MixinExtras @WrapMethod on
 * ClientConfigManager.getCurrentProfile: the kept answer while XaeroLibEpoch says nothing changed and the kept profile is
 * the one returned last (or the answer was null); otherwise XaeroLib's method runs and its answer is kept when nothing
 * changed during the call (see XaeroLibMemos for why the answer is identical). A manager whose answers may not be kept gets
 * a stand-aside mark, so its reads go straight to XaeroLib until the next change. No XaeroLib code is carried.
 */
@Mixin(value = ClientConfigManager.class, remap = false)
public abstract class XlCurrentProfileMemoMixin {
    @Shadow
    @Final
    private SingleConfigManager<Config> primaryConfigManager;

    @Shadow
    private ConfigProfile lastFetchedProfile;

    @Unique
    private XaeroLibMemos.ProfileMemo bons$xaerolibC49ProfileMemo;

    @WrapMethod(method = "getCurrentProfile")
    private ConfigProfile bons$xaerolibC49CurrentProfile(Operation<ConfigProfile> original) {
        if (!XaeroLibMemos.profileEnabled) return original.call();
        XaeroLibMemos.ProfileMemo kept = this.bons$xaerolibC49ProfileMemo;
        boolean hit = false;
        if (kept != null && XaeroLibEpoch.holds(kept.epoch)) {
            if (!kept.keep) return original.call();
            if (kept.profile == null || kept.profile == this.lastFetchedProfile) {
                if (!XaeroLibMemos.PROFILE_SHADOW) return kept.profile;
                hit = true;
            }
        }
        long e = XaeroLibEpoch.stable();
        ConfigProfile result = original.call();
        if (hit) XaeroLibMemos.profileShadow(kept, result);
        this.bons$xaerolibC49ProfileMemo = e >= 0 && XaeroLibEpoch.holds(e)
                ? new XaeroLibMemos.ProfileMemo(e, result, XaeroLibMemos.profileMemoable(this.primaryConfigManager)) : null;
        return result;
    }
}
