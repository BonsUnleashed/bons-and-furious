package bons.furious.mixin.opposing_force_coo49;

import bons.furious.patch.opposing_force_coo49.StealthMemo;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * opposing_force_stealth_tick_memo (Opposing Force 2.0.3, MIT; both sides; trade-off, off by default), on
 * ForgeEvents.onLivingVisibility: @Inject at HEAD names the event's entity's memo for this tick (StealthMemo.begin), a
 * MixinExtras @WrapOperation on its ItemStack.getAttributeModifiers (m_41638_) call (once per equipment slot) answers from
 * the memo while the slot holds the same stack object, @Inject at RETURN clears the name (StealthMemo.end). Our own logic
 * only; nothing of the mod is carried.
 */
@Mixin(targets = "com.unusualmodding.opposing_force.events.ForgeEvents", remap = false)
public abstract class StealthVisibilityMemoMixin {
    private static final String HANDLER = "onLivingVisibility(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingVisibilityEvent;)V";

    @Inject(method = HANDLER, at = @At("HEAD"), require = 1, allow = 1)
    private static void bons$opposingForceCoo49Begin(LivingEvent.LivingVisibilityEvent event, CallbackInfo ci) {
        StealthMemo.begin(event);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = HANDLER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41638_(Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;"),
            require = 1, allow = 1)
    private static Multimap bons$opposingForceCoo49Modifiers(ItemStack stack, EquipmentSlot slot, Operation<Multimap> original) {
        return StealthMemo.modifiers(stack, slot, original);
    }

    @Inject(method = HANDLER, at = @At("RETURN"), require = 1, allow = 1)
    private static void bons$opposingForceCoo49End(LivingEvent.LivingVisibilityEvent event, CallbackInfo ci) {
        StealthMemo.end();
    }
}
