package bons.furious.mixin.attribute_lookup;

import bons.furious.patch.attribute_lookup.EffectRefreshKeep;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_effect_refresh_modifier_keep (Minecraft 1.20.1, Forge 47.4.28; server; since 1.0.49): the MobEffect
 * removeAttributeModifiers (m_6386_) and addAttributeModifiers (m_6385_) calls of LivingEntity.onEffectUpdated (m_141973_,
 * the forced branch on the server) are skipped together when the pair would change nothing (EffectRefreshKeep.unchanged);
 * everything else in the method, and every other mod's hook on it, runs as shipped. Our own logic only.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityEffectRefreshMixin {
    @WrapOperation(method = "m_141973_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;m_6386_(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V"))
    private void bons$keepRemove(MobEffect effect, LivingEntity entity, AttributeMap map, int amplifier, Operation<Void> original) {
        if (EffectRefreshKeep.unchanged(effect, map, amplifier)) return;
        original.call(effect, entity, map, amplifier);
    }

    @WrapOperation(method = "m_141973_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;m_6385_(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V"))
    private void bons$keepAdd(MobEffect effect, LivingEntity entity, AttributeMap map, int amplifier, Operation<Void> original) {
        if (EffectRefreshKeep.unchanged(effect, map, amplifier)) {
            EffectRefreshKeep.count();
            return;
        }
        original.call(effect, entity, map, amplifier);
    }
}
