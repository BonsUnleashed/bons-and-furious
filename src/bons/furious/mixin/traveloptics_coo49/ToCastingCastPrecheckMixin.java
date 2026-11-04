package bons.furious.mixin.traveloptics_coo49;

import bons.furious.patch.traveloptics_coo49.ToCasterEffects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * traveloptics_caster_effect_precheck (T.O Magic 'n Extras 6.3.0, All Rights Reserved; both sides; exact): MixinExtras
 * @WrapOperation on the LivingEntity.hasEffect (m_21023_) call of CastingHandler.onEntityCast (the living tick handler): its
 * answer is only read inside the handler's "instanceof AbstractSpellCastingMob" branch, so for any other entity the call is
 * not made and false stands in. See ToCasterEffects. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.gametechbc.traveloptics.effects.Casting.CastingHandler", remap = false)
public abstract class ToCastingCastPrecheckMixin {
    @WrapOperation(method = "onEntityCast(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21023_(Lnet/minecraft/world/effect/MobEffect;)Z"),
            require = 1, allow = 1, remap = false)
    private static boolean bons$travelopticsCoo49CastingEffect(LivingEntity entity, MobEffect effect, Operation<Boolean> original) {
        return ToCasterEffects.hasEffect(entity, effect, original);
    }
}
