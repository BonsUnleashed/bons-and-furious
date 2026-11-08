package bons.furious.mixin.peyroscythe_coo49;

import bons.furious.patch.peyroscythe_coo49.PeyroAuras;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * peyroscythe_aura_apply_interval (Peyro's Scythes &amp; Spells 1.21.1-1.3.2 for NeoForge, GPLv3; both sides; trade-off, off
 * by default): MixinExtras @WrapOperation on the LivingEntity.addEffect(MobEffectInstance) call in the Crimson Moon (Moon
 * Frenzy, 40 ticks; a successful add also writes the entity's moon frenzy marks) aura's tick (CrimsonMoon.tick): on ticks
 * that are not a beat of the aura's own age (age % interval != 0) the call answers false without running; on beat ticks it
 * runs as shipped. See PeyroAuras. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.rinko1231.peyroscythe.spellentity.CrimsonMoon", remap = false)
public abstract class PeyroCrimsonMoonIntervalMixin {
    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            require = 1, allow = 1)
    private boolean bons$peyroscytheCoo49CrimsonMoonBeat(LivingEntity living, MobEffectInstance effect, Operation<Boolean> original) {
        if (PeyroAuras.offBeat(this)) return false;
        return original.call(living, effect);
    }
}
