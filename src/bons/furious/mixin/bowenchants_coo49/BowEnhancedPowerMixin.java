package bons.furious.mixin.bowenchants_coo49;

import bons.furious.patch.bowenchants_coo49.BowFixes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * bowenchants_strict_enhanced_power (Advanced Bow Enchantments 1.2.0, server side of the join; gameplay fix, off by
 * default): BowEnchantsEvents.onEntityJoinLevel's one read of the owner's main-hand Enhanced Power level goes through
 * BowFixes.enhancedPowerLevel (0 for a projectile loaded from disk, an arrow that already carries the done mark, or a
 * non-arrow projectile whose owner is not drawing that item). Our own logic only (All Rights Reserved target; none of its
 * code is carried).
 */
@Mixin(targets = "com.doug.bowenchants.events.BowEnchantsEvents", remap = false)
public abstract class BowEnhancedPowerMixin {
    @WrapOperation(method = "onEntityJoinLevel(Lnet/minecraftforge/event/entity/EntityJoinLevelEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;m_44843_(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I"),
            require = 1, allow = 1)
    private int bons$bowenchants_coo49EnhancedPower(Enchantment enchantment, ItemStack mainhand, Operation<Integer> original, EntityJoinLevelEvent event) {
        return BowFixes.enhancedPowerLevel(original.call(enchantment, mainhand), mainhand, event);
    }
}
