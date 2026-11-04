package bons.furious.mixin.mekaweapons_coo49;

import bons.furious.patch.mekaweapons_coo49.MekaWeaponsFixes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meranha.mekaweapons.items.ItemMekaBow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mekaweapons_bow_offhand_arrow (gameplay fix, off by default; Mekanism Weapons 3.0 and 4.2.1): in ItemMekaBow.customArrow
 * the player.getMainHandItem() read that picks the weapon whose modules the arrow gets returns the player's use item when
 * that is a Meka-Bow (releaseUsing still holds the bow as the use item, in either hand); otherwise the original read. See
 * MekaWeaponsFixes. COO 5.8 hooks the same method (mekaweapons.MixinMekaBowOffhandArrow, option
 * mekaweapons.bowOffhandArrowStats).
 */
@Mixin(value = ItemMekaBow.class, remap = false)
public abstract class MekaBowOffhandArrowMixin {
    @WrapOperation(method = "customArrow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_21205_()Lnet/minecraft/world/item/ItemStack;"),
            require = 1, allow = 1)
    private ItemStack bons$bowInUse(Player player, Operation<ItemStack> original) {
        if (MekaWeaponsFixes.bowOffhand) {
            ItemStack using = player.m_21211_();
            if (using.m_41720_() instanceof ItemMekaBow) {
                MekaWeaponsFixes.announce(4, "mekaweapons_bow_offhand_arrow", "Meka-Bow arrows take the modules of the bow that shot them");
                return using;
            }
        }
        return original.call(player);
    }
}
