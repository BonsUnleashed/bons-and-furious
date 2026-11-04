package bons.furious.mixin.peyroscythe_coo49;

import bons.furious.patch.peyroscythe_coo49.PeyroAuras;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * peyroscythe_aura_apply_interval (Peyro's Scythes &amp; Spells 1.20.1-1.3.3, GPLv3; both sides; trade-off, off by
 * default): MixinExtras @WrapOperation on the LivingEntity.addEffect (m_7292_) call in the Golden Bell (Holy Bell Suppression, 40 ticks) aura's tick
 * (GoldenBellEntity.m_8119_): on ticks that are not a beat of the aura's own age (age % interval != 0) the call answers false
 * without running; on beat ticks it runs as shipped. See PeyroAuras. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.rinko1231.peyroscythe.spellentity.holy.GoldenBellEntity", remap = false)
public abstract class PeyroGoldenBellIntervalMixin {
    @WrapOperation(method = "m_8119_()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_7292_(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            require = 1, allow = 1)
    private boolean bons$peyroscytheCoo49GoldenBellBeat(LivingEntity living, MobEffectInstance effect, Operation<Boolean> original) {
        if (PeyroAuras.offBeat(this)) return false;
        return original.call(living, effect);
    }
}