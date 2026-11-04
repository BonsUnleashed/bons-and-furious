package bons.furious.mixin.mahoutsukai_coo49;

import net.minecraftforge.common.extensions.IForgeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mahoutsukai_projector_enchant_precheck (Forge's IForgeItem, client dist; applied only while Mahou Tsukai is installed): a
 * probe mixin. Priority 3000, so Mixin applies it after every other mod's mixin on the interface; MahouPlugin.postApply
 * then checks that getEnchantmentLevel(ItemStack, Enchantment) is as shipped (MahouProjectorProbe). It only adds one empty
 * default method with a unique name (an interface mixin may only add public members; the member keeps an offline apply
 * from dropping the probe).
 */
@Mixin(value = IForgeItem.class, priority = 3000, remap = false)
public interface MahouForgeItemProbeMixin {
    @Unique
    default void bons$mahoutsukaiCoo49ProbeForgeItem() {
    }
}
