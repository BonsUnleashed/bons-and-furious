package bons.furious.mixin.dungeonsdelight_coo49;

import bons.furious.patch.dungeonsdelight_coo49.TenacityGuard;
import net.yirmiri.dungeonsdelight.common.effect.TenacityEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * dungeonsdelight_tenacity_zero_interval_guard (Dungeons Delight 1.2.10 / 1.3.1, both sides): HEAD of
 * TenacityEffect.isDurationEffectTick answers true only when applyInterval - amplifier * 2 == 0 (the input whose % throws
 * ArithmeticException and crashes the server tick); every other input runs the original. See TenacityGuard. COO 5.8 hooks
 * this method (dungeonsdelight.MixinDdTenacityInterval, option dungeonsdelight.perPlayerTenacityInterval, which includes a
 * zero guard): a HEAD injection composes with another mod's injection or overwrite.
 */
@Mixin(value = TenacityEffect.class, remap = false)
public abstract class TenacityZeroIntervalMixin {
    @Shadow
    int applyInterval;

    @Inject(method = "m_6584_", at = @At("HEAD"), cancellable = true)
    private void bons$tenacityZeroInterval(int duration, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (TenacityGuard.zeroDivisor(this.applyInterval, amplifier)) cir.setReturnValue(true);
    }
}
