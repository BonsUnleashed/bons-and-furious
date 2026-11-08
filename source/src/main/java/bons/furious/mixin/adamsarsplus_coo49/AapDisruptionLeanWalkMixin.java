package bons.furious.mixin.adamsarsplus_coo49;

import bons.furious.patch.adamsarsplus_coo49.AapEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * adamsarsplus_disruption_lean_walk (Adam's Ars Plus 1.21.1-6.0.5, LGPL-3.0: no code carried; both sides; exact).
 * DisruptionEffect.applyEffectTick: the same index walk and removeEffect calls without the two stream().toList() copies
 * per step (AapEffects.leanDisruption), then true, as the original answers. Priority 1000: the clean purge
 * (AapDisruptionPurgeMixin, priority 900) runs first when both switches are on.
 */
@Mixin(targets = "adamsmods.adamsarsplus.common.mob_effects.DisruptionEffect", remap = false)
public abstract class AapDisruptionLeanWalkMixin {
    @Inject(method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z", at = @At("HEAD"), cancellable = true)
    private void bons$adamsarsplusLeanDisruption(LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (AapEffects.leanDisruption(entity)) cir.setReturnValue(true);
    }
}
