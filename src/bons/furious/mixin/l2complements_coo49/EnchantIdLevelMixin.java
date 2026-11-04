package bons.furious.mixin.l2complements_coo49;

import bons.furious.patch.l2complements_coo49.EnchantIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * l2complements_enchant_id_string_compare (L2 Complements 2.6.1, both sides): a cancellable HEAD inject on
 * EnchUtils.getTagEnchantmentLevel(Enchantment, ItemStack) that answers with EnchantIds.level (the same walk comparing id
 * strings instead of parsing them); an enchantment without a registry key runs the shipped method. Our own logic only (All
 * Rights Reserved target; none of its code is carried).
 */
@Mixin(targets = "dev.xkmc.l2complements.events.EnchUtils", remap = false)
public abstract class EnchantIdLevelMixin {
    @Inject(method = "getTagEnchantmentLevel(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$l2complements_coo49Level(Enchantment enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!EnchantIds.enabled) return;
        int level = EnchantIds.level(enchantment, stack);
        if (level != EnchantIds.UNKNOWN) cir.setReturnValue(level);
    }
}
