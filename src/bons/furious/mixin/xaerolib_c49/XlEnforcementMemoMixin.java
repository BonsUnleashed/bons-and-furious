package bons.furious.mixin.xaerolib_c49;

import bons.furious.patch.xaerolib_c49.XaeroLibEpoch;
import bons.furious.patch.xaerolib_c49.XaeroLibMemos;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.client.config.option.value.redirect.ClientOptionValueRedirectorManager;
import xaero.lib.client.config.sync.SyncedConfigManager;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.profile.ConfigProfile;

/**
 * xaerolib_enforcement_read_memo (XaeroLib 1.7.3, client, since 1.0.49; exact). MixinExtras @WrapOperation on the nested
 * getEffective(config, IGNORE_ENFORCEMENT_IF_EDITOR) call in ClientConfigManager.shouldIgnoreServerEnforcement(ConfigProfile,
 * ConfigOption): the kept Boolean while XaeroLibEpoch says nothing changed, the config is the same object and a world is
 * loaded or not as before; otherwise the call runs and a non-null answer is kept when the epoch did not move during it (see
 * XaeroLibMemos). Needs xaerolib_current_profile_memo's change tracking (stands down without it). No XaeroLib code is
 * carried.
 */
@Mixin(value = ClientConfigManager.class, remap = false)
public abstract class XlEnforcementMemoMixin {
    @Shadow
    @Final
    private SyncedConfigManager serverSynced;

    @Shadow
    @Final
    private ClientOptionValueRedirectorManager redirectorManager;

    @Unique
    private XaeroLibMemos.EnforcementMemo bons$xaerolibC49EnforcementMemo;

    @WrapOperation(method = "shouldIgnoreServerEnforcement(Lxaero/lib/common/config/profile/ConfigProfile;Lxaero/lib/common/config/option/ConfigOption;)Z",
            at = @At(value = "INVOKE", target = "Lxaero/lib/client/config/ClientConfigManager;getEffective(Lxaero/lib/common/config/profile/ConfigProfile;Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;"))
    private Object bons$xaerolibC49Enforcement(ClientConfigManager self, ConfigProfile config, ConfigOption<?> option, Operation<Object> original) {
        if (!XaeroLibMemos.enforcementEnabled || !XaeroLibMemos.TRACKING) return original.call(self, config, option);
        boolean levelNull = XaeroLibMemos.levelNull();
        XaeroLibMemos.EnforcementMemo kept = this.bons$xaerolibC49EnforcementMemo;
        boolean hit = false;
        if (kept != null && kept.config == config && kept.levelNull == levelNull && XaeroLibEpoch.holds(kept.epoch)) {
            if (!kept.keep) return original.call(self, config, option);
            if (!XaeroLibMemos.ENFORCEMENT_SHADOW) return kept.value;
            hit = true;
        }
        long e = XaeroLibEpoch.stable();
        Object value = original.call(self, config, option);
        if (hit) XaeroLibMemos.enforcementShadow(kept, value);
        this.bons$xaerolibC49EnforcementMemo = e >= 0 && XaeroLibEpoch.holds(e)
                ? new XaeroLibMemos.EnforcementMemo(e, config, levelNull, value,
                        value != null && XaeroLibMemos.enforcementMemoable(config, this.serverSynced, this.redirectorManager)) : null;
        return value;
    }
}
