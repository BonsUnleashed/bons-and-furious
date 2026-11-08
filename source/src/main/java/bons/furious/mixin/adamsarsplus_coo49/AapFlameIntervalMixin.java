package bons.furious.mixin.adamsarsplus_coo49;

import bons.furious.patch.adamsarsplus_coo49.AapEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * adamsarsplus_flame_aura_interval (Adam's Ars Plus 1.21.1-6.0.5, LGPL-3.0: no code carried; both sides; trade-off, off by
 * default). FlameDeityAuraEffect.applyEffectTick searches only when at least N ticks of the holder have passed since its
 * last search (AapEffects.skipAura, the holder's clock from AapAuraClockMixin); a skipped tick answers true, as the original
 * does. The search itself is unchanged.
 */
@Mixin(targets = "adamsmods.adamsarsplus.common.mob_effects.FlameDeityAuraEffect", remap = false)
public abstract class AapFlameIntervalMixin {
    @Inject(method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z", at = @At("HEAD"), cancellable = true)
    private void bons$adamsarsplusAuraInterval(LivingEntity holder, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (AapEffects.skipAura(holder)) cir.setReturnValue(true);
    }
}
