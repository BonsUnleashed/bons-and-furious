package bons.furious.mixin.distanthorizons_c2;

import bons.furious.patch.distanthorizons_c2.JoinConfigResend;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * distanthorizons_join_config_resend (Distant Horizons 3.3.3 (1.21.1), LGPL-3.0; client; a fix, since 1.0.34): right after
 * SharedApi.setDhWorld's ThreadPoolUtil.setupThreadPools() (the load branch; the same call
 * distanthorizons_world_change_biome_reset hooks before), a client world that has not received the server's session
 * config sends it again (see {@link JoinConfigResend}).
 */
@Mixin(value = SharedApi.class, remap = false)
public abstract class SharedApiJoinConfigResendMixin {
    @Inject(method = "setDhWorld", require = 1, allow = 1, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lcom/seibel/distanthorizons/core/util/threading/ThreadPoolUtil;setupThreadPools()V"))
    private static void bons$resendConfigAfterPools(AbstractDhWorld newWorld, CallbackInfo ci) {
        JoinConfigResend.afterPools(newWorld);
    }
}
