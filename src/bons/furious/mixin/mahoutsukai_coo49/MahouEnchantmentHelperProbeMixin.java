package bons.furious.mixin.mahoutsukai_coo49;

import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mahoutsukai_projector_enchant_precheck (vanilla EnchantmentHelper, client dist; applied only while Mahou Tsukai is
 * installed): a probe mixin. Priority 3000, so Mixin applies it after every other mod's mixin on the class;
 * MahouPlugin.postApply then checks that getItemEnchantmentLevel (m_44843_) and Forge's getTagEnchantmentLevel are as shipped
 * (MahouProjectorProbe). It only adds one empty private static method (so an offline apply keeps it).
 */
@Mixin(value = EnchantmentHelper.class, priority = 3000, remap = false)
public abstract class MahouEnchantmentHelperProbeMixin {
    @Unique
    private static void bons$mahoutsukaiCoo49ProbeEnchantmentHelper() {
    }
}
