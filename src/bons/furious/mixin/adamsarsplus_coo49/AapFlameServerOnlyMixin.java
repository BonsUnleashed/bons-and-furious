package bons.furious.mixin.adamsarsplus_coo49;

import bons.furious.patch.adamsarsplus_coo49.AapEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * adamsarsplus_flame_aura_server_only (Adam's Ars Plus 1.20.1-5.0.3, LGPL-3.0: no code carried; client mixin; trade-off,
 * off by default). FlameDeityAuraEffect.applyEffectTick returns at once on a client level (AapEffects.skipClientAura); the
 * integrated server's own sweeps are untouched.
 */
@Mixin(targets = "com.adamsmods.adamsarsplus.entities.effects.FlameDeityAuraEffect", remap = false)
public abstract class AapFlameServerOnlyMixin {
    @Inject(method = "m_6742_(Lnet/minecraft/world/entity/LivingEntity;I)V", at = @At("HEAD"), cancellable = true)
    private void bons$adamsarsplusAuraServerOnly(LivingEntity holder, int amplifier, CallbackInfo ci) {
        if (AapEffects.skipClientAura(holder)) ci.cancel();
    }
}
