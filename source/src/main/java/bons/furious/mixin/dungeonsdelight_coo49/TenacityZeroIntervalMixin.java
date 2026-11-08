package bons.furious.mixin.dungeonsdelight_coo49;

import bons.furious.patch.dungeonsdelight_coo49.TenacityGuard;
import net.yirmiri.dungeonsdelight.common.effect.TenacityEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * dungeonsdelight_tenacity_zero_interval_guard (Dungeons Delight, AZURUNE licence; 1.21.1 tested build: Dungeon's Delight
 * 1.5.1 for NeoForge 1.21.1, neoforge-dungeonsdelight-1.21.1-1.5.1.jar; both sides): HEAD of
 * TenacityEffect.shouldApplyEffectTickThisTick answers true only when applyInterval - amplifier * 2 == 0 (the input whose %
 * throws ArithmeticException and crashes the tick); every other input runs the original. See TenacityGuard.
 *
 * Ported to 1.21.1: Minecraft 1.21 renamed MobEffect.isDurationEffectTick to shouldApplyEffectTickThisTick; TenacityEffect
 * (now a MobEffect of its own) keeps duration % (applyInterval - amplifier * 2) == 0 with the shared applyInterval field.
 */
@Mixin(value = TenacityEffect.class, remap = false)
public abstract class TenacityZeroIntervalMixin {
    @Shadow
    int applyInterval;

    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void bons$tenacityZeroInterval(int duration, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (TenacityGuard.zeroDivisor(this.applyInterval, amplifier)) cir.setReturnValue(true);
    }
}
