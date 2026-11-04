package bons.furious.mixin.enigmaticaddons_coo49;

import bons.furious.patch.enigmaticaddons_coo49.FrostSkip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * enigmaticaddons_idle_frost_skip (Enigmatic Addons 1.2.7, both sides; All Rights Reserved: no code of the mod is carried).
 * MixinExtras @WrapOperation on the EnchantmentHelper.getEnchantmentLevel call of AddonEventHandler.onTick (its only one:
 * the Frost Protection level at the end of the handler, run for every living entity on every tick). The handler then
 * writes 0 frozen ticks at level 16 or more, or max(frozen ticks - 1, 0) on some ticks at levels 1 to 15; with no frozen
 * ticks both writes set 0 on a 0 (SynchedEntityData.set of an equal value does nothing). So while the entity has no frozen
 * ticks the lookup is left out and 0 is passed on (the handler then writes nothing), FrostSkip.skipLevelLookup;
 * otherwise the lookup runs as shipped.
 */
@Mixin(targets = "auviotre.enigmatic.addon.handlers.AddonEventHandler", remap = false)
public abstract class EaFrostEnchantMixin {
    @WrapOperation(method = "onTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;m_44836_(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/entity/LivingEntity;)I"),
            require = 1, allow = 1)
    private int bons$enigmaticaddonsCoo49FrostLevel(Enchantment enchantment, LivingEntity entity, Operation<Integer> original) {
        return FrostSkip.skipLevelLookup(entity) ? 0 : original.call(enchantment, entity);
    }
}
