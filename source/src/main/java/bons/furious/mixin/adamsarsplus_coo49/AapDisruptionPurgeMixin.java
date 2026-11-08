package bons.furious.mixin.adamsarsplus_coo49;

import bons.furious.patch.adamsarsplus_coo49.AapEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * adamsarsplus_disruption_clean_purge (Adam's Ars Plus 1.21.1-6.0.5, LGPL-3.0: no code carried; gameplay fix, off by
 * default). DisruptionEffect.applyEffectTick no longer removes effects from inside LivingEntity.tickEffects' loop: on the
 * server it marks the victim (AapEffects.purgeLater), and AapPurgeTailMixin removes every beneficial effect when
 * tickEffects' loop ends; on the client it does nothing. The skipped call answers true, as the original does. Priority 900,
 * so this runs before the lean walk's callback.
 */
@Mixin(targets = "adamsmods.adamsarsplus.common.mob_effects.DisruptionEffect", remap = false, priority = 900)
public abstract class AapDisruptionPurgeMixin {
    @Inject(method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z", at = @At("HEAD"), cancellable = true)
    private void bons$adamsarsplusPurgeLater(LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (AapEffects.purgeLater(entity)) cir.setReturnValue(true);
    }
}
