package bons.furious.mixin.dungeonsdelight_coo49;

import bons.furious.patch.dungeonsdelight_coo49.TenacityIntervalHolder;
import bons.furious.patch.dungeonsdelight_coo49.TenacityIntervals;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.yirmiri.dungeonsdelight.common.effect.TenacityEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dungeonsdelight_tenacity_per_player_interval (Dungeon's Delight 1.5.1 for NeoForge 1.21.1): in
 * MobEffectInstance.tick(LivingEntity, Runnable), the shouldApplyEffectTickThisTick call of a TenacityEffect held by an
 * entity with its own recorded interval answers with that interval (TenacityIntervals.tick); every other effect and entity
 * calls the original.
 *
 * Ported to 1.21.1: MobEffectInstance.tick asks this.effect.value().shouldApplyEffectTickThisTick(duration or tickCount,
 * amplifier) once (isDurationEffectTick in 1.20.1).
 */
@Mixin(value = MobEffectInstance.class, remap = false)
public abstract class MobEffectInstanceTenacityMixin {
    @WrapOperation(method = "tick(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffect;shouldApplyEffectTickThisTick(II)Z"),
            require = 1, allow = 1)
    private boolean bons$ownTenacityInterval(MobEffect effect, int duration, int amplifier, Operation<Boolean> original,
                                            @Local(argsOnly = true) LivingEntity entity) {
        if (TenacityIntervals.enabled && effect instanceof TenacityEffect && entity instanceof TenacityIntervalHolder holder) {
            int own = holder.bons$tenacityInterval();
            if (own != 0) return TenacityIntervals.tick(duration, amplifier, own);
        }
        return original.call(effect, duration, amplifier);
    }
}
