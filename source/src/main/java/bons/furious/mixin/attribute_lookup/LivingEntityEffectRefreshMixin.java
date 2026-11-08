package bons.furious.mixin.attribute_lookup;

import bons.furious.patch.attribute_lookup.EffectRefreshKeep;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_effect_refresh_modifier_keep (Minecraft 1.21.1 on NeoForge 21.1.252; server): the MobEffect
 * removeAttributeModifiers(AttributeMap) and addAttributeModifiers(AttributeMap, int) calls of LivingEntity.onEffectUpdated
 * (the forced branch on the server, before refreshDirtyAttributes) are skipped together when the pair would change nothing
 * (EffectRefreshKeep.unchanged). The remove call has no amplifier argument, so its wrapper reads the amplifier from the
 * method's MobEffectInstance argument, the value the add call is given right after it. Everything else in the method, and
 * every other mod's hook on it, runs as shipped. Our own logic only.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityEffectRefreshMixin {
    @WrapOperation(method = "onEffectUpdated(Lnet/minecraft/world/effect/MobEffectInstance;ZLnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;removeAttributeModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeMap;)V"))
    private void bons$keepRemove(MobEffect effect, AttributeMap map, Operation<Void> original, @Local(argsOnly = true) MobEffectInstance instance) {
        if (instance != null && EffectRefreshKeep.unchanged(effect, map, instance.getAmplifier())) return;
        original.call(effect, map);
    }

    @WrapOperation(method = "onEffectUpdated(Lnet/minecraft/world/effect/MobEffectInstance;ZLnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;addAttributeModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V"))
    private void bons$keepAdd(MobEffect effect, AttributeMap map, int amplifier, Operation<Void> original) {
        if (EffectRefreshKeep.unchanged(effect, map, amplifier)) {
            EffectRefreshKeep.count();
            return;
        }
        original.call(effect, map, amplifier);
    }
}
