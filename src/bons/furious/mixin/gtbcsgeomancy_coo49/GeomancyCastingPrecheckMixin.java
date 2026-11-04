package bons.furious.mixin.gtbcsgeomancy_coo49;

import bons.furious.patch.gtbcsgeomancy_coo49.GeomancyGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * gtbcs_geomancy_caster_effect_precheck (GTBC's Geomancy Plus 2.0.0, All Rights Reserved; both sides): MixinExtras
 * @WrapOperation on the LivingEntity.hasEffect (m_21023_) call in CastingHandler.onEntityCast (a living tick listener).
 * The handler stores that answer and reads it only inside its "instanceof AbstractSpellCastingMob" branch, so for every
 * other entity the answer is never used: the wrapper then answers false without the call (a pure map lookup). For spell
 * casting mobs the call runs as shipped. See GeomancyGates. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.gametechbc.gtbcs_geomancy_plus.effects.Casting.CastingHandler", remap = false)
public abstract class GeomancyCastingPrecheckMixin {
    @WrapOperation(method = "onEntityCast(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21023_(Lnet/minecraft/world/effect/MobEffect;)Z"),
            require = 1, allow = 1)
    private static boolean bons$gtbcsgeomancyCoo49CastingEffect(LivingEntity entity, MobEffect effect, Operation<Boolean> original) {
        if (GeomancyGates.precheck && !GeomancyGates.caster(entity)) return false;
        return original.call(entity, effect);
    }
}
