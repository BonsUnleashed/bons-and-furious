package bons.furious.mixin.mahoutsukai_coo49;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mahoutsukai_projector_enchant_precheck (vanilla ItemStack, client dist; applied only while Mahou Tsukai is installed): a
 * probe mixin. Priority 3000, so Mixin applies it after every other mod's mixin on the class; MahouPlugin.postApply then
 * checks that getTag (m_41783_), getEnchantmentTags (m_41785_), isEmpty (m_41619_) and getItem (m_41720_) are as shipped
 * (MahouProjectorProbe). It only adds one empty private static method (so an offline apply keeps it).
 */
@Mixin(value = ItemStack.class, priority = 3000, remap = false)
public abstract class MahouItemStackProbeMixin {
    @Unique
    private static void bons$mahoutsukaiCoo49ProbeItemStack() {
    }
}
