package bons.furious.mixin.celestialenchantments_coo49;

import bons.furious.patch.celestialenchantments_coo49.CeTickPrecheck;
import com.xiaoyue.celestial_enchantments.utils.IEnchUtils;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * celestialenchantments_tick_enchant_precheck (Celestial Enchantments 1.3.7, both sides): in
 * CEGeneralEventHandler.onLivingTick, only past its "tickCount % 4" test, three Redirects (the event captured from the
 * handler's argument): getAllEnch answers an empty map without the call when no slot's stack has a tag; the per-slot
 * loop's EquipmentSlot.values() answers no slots after an empty getAllEnch map of the same call; the per-slot getEnch
 * answers an empty map for a slot whose stack has no tag. Otherwise the shipped calls. Our own logic only (LGPL-2.1
 * target; none of its code is carried).
 */
@Mixin(targets = "com.xiaoyue.celestial_enchantments.event.CEGeneralEventHandler", remap = false)
public abstract class CeTickPrecheckMixin {
    @Redirect(method = "onLivingTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lcom/xiaoyue/celestial_enchantments/utils/IEnchUtils;getAllEnch(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Map;"),
            require = 1, allow = 1)
    @SuppressWarnings("rawtypes")
    private static Map bons$celestialenchantments_coo49AllEnch(LivingEntity entity, LivingEvent.LivingTickEvent event) {
        Map m = CeTickPrecheck.untaggedTick(entity) ? CeTickPrecheck.none() : IEnchUtils.getAllEnch(entity);
        CeTickPrecheck.allEnchDone(event, m);
        return m;
    }

    @Redirect(method = "onLivingTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EquipmentSlot;values()[Lnet/minecraft/world/entity/EquipmentSlot;"),
            require = 1, allow = 1)
    private static EquipmentSlot[] bons$celestialenchantments_coo49Slots(LivingEvent.LivingTickEvent event) {
        return CeTickPrecheck.slots(event);
    }

    @Redirect(method = "onLivingTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lcom/xiaoyue/celestial_enchantments/utils/IEnchUtils;getEnch(Lnet/minecraft/world/entity/LivingEntity;[Lnet/minecraft/world/entity/EquipmentSlot;)Ljava/util/Map;"),
            require = 1, allow = 1)
    @SuppressWarnings("rawtypes")
    private static Map bons$celestialenchantments_coo49SlotEnch(LivingEntity entity, EquipmentSlot[] slots) {
        return CeTickPrecheck.slotEnch(entity, slots) ? CeTickPrecheck.none() : IEnchUtils.getEnch(entity, slots);
    }
}
