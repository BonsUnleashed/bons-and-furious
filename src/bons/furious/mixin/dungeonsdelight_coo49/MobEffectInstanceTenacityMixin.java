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
 * dungeonsdelight_tenacity_per_player_interval: in MobEffectInstance.tick(LivingEntity, Runnable), the
 * isDurationEffectTick call of a TenacityEffect held by an entity with its own recorded interval answers with that interval
 * (TenacityIntervals.tick); every other effect and entity calls the original.
 */
@Mixin(value = MobEffectInstance.class, remap = false)
public abstract class MobEffectInstanceTenacityMixin {
    @WrapOperation(method = "m_19552_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffect;m_6584_(II)Z"),
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
