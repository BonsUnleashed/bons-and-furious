package bons.furious.mixin.arselemental_coo49;

import alexthw.ars_elemental.event.DamageEvents;
import bons.furious.patch.arselemental_coo49.HealBoostFix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * arselemental_heal_boost_focus_only (Ars Elemental 0.6.7.8 / 0.6.8.0, both sides): the first LivingHealEvent.setAmount in
 * DamageEvents.handleHealing (the 1.5 bonus) runs only when HealBoostFix keeps the bonus. See HealBoostFix.
 */
@Mixin(value = DamageEvents.class, remap = false)
public abstract class HealBoostFocusMixin {
    @WrapOperation(method = "handleHealing", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/event/entity/living/LivingHealEvent;setAmount(F)V", ordinal = 0),
            require = 1, allow = 1)
    private static void bons$earthFocusOnly(LivingHealEvent event, float amount, Operation<Void> original) {
        if (HealBoostFix.keepBonus(event)) original.call(event, amount);
    }
}
