package bons.furious.mixin.domesticationinnovation_coo49;

import bons.furious.patch.domesticationinnovation_coo49.EnchantIds;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * domesticationinnovation_enchant_id_compare (Domestication Innovation 1.7.1, both sides): the one
 * EnchantmentHelper.getEnchantmentId(CompoundTag) call in TameableUtils.getEnchantLevel answers the comparison that
 * follows it from the key's text (EnchantIds): the key itself for a match, null for a certain non-match, the original
 * parse otherwise. COO 5.8 hooks getEnchantLevel (domesticationinnovation.MixinDITameableUtils, option
 * domesticationinnovation.fastEnchantLookup); a WrapOperation composes with another mod's wrap of the call.
 */
@Mixin(value = TameableUtils.class, remap = false)
public abstract class TameableUtilsEnchantIdMixin {
    @WrapOperation(method = "getEnchantLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;m_182446_(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1, allow = 1)
    private static ResourceLocation bons$enchantIdForCompare(CompoundTag entry, Operation<ResourceLocation> original,
                                                             @Local(argsOnly = true) Enchantment enchantment) {
        return EnchantIds.resolve(entry, enchantment, original);
    }
}
