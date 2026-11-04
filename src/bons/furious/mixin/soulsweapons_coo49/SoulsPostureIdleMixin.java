package bons.furious.mixin.soulsweapons_coo49;

import bons.furious.patch.soulsweapons_coo49.SoulsPostureIdle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * soulsweapons_idle_posture_max_skip (Soulslike Weaponry 1.4.10, same bytes in 1.4.8; CC0-1.0; both sides; since 1.0.49).
 * Trade-off, off by default. ModEvents.onLivingEntityTicks (every living entity, every tick, both sides) asks
 * "posture >= EntityPosture.getMaxPostureLoss(entity) && getMaxPostureLoss(entity) != 0" to decide a posture break.
 * MixinExtras @WrapOperation on the FIRST getMaxPostureLoss call: while the entity's posture is 0 or below
 * (SoulsPostureIdle) it answers Integer.MAX_VALUE without computing the maximum, so the test is false and the second call
 * is never made; with posture above 0 the call runs as shipped. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "net.soulsweaponry.events.ModEvents", remap = false)
public abstract class SoulsPostureIdleMixin {
    @WrapOperation(method = "onLivingEntityTicks(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/soulsweaponry/api/entitystats/EntityPosture;getMaxPostureLoss(Lnet/minecraft/world/entity/LivingEntity;)I",
                    ordinal = 0, remap = false),
            require = 1, allow = 1, remap = false)
    private static int bons$soulsweaponsCoo49IdlePostureMax(LivingEntity entity, Operation<Integer> original) {
        return SoulsPostureIdle.maxPostureLoss(entity, original);
    }
}
