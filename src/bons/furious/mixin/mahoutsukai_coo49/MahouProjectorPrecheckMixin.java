package bons.furious.mixin.mahoutsukai_coo49;

import bons.furious.patch.mahoutsukai_coo49.MahouProjector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * mahoutsukai_projector_enchant_precheck (Mahou Tsukai 1.20.1-v1.34.81, All Rights Reserved; client): @Redirect of the
 * EffectUtil.hasEnchantment call in RenderProjectorEnchant.renderProjectorEnchantment (every living entity, every frame,
 * once per armour stack): "false" without the call for a stack without NBT whose item keeps Forge's default
 * enchantment-level lookup while that lookup chain is as shipped; else the same call, made directly (a @Redirect, not a
 * @WrapOperation: no Operation and no argument array on the path where the stock call runs). See MahouProjector. Our own
 * logic only; nothing of the mod is carried.
 */
@Mixin(targets = "stepsword.mahoutsukai.render.enchant.RenderProjectorEnchant", remap = false)
public abstract class MahouProjectorPrecheckMixin {
    @Redirect(method = "renderProjectorEnchantment(Lnet/minecraftforge/client/event/RenderLivingEvent$Pre;)V",
            at = @At(value = "INVOKE", target = "Lstepsword/mahoutsukai/util/EffectUtil;hasEnchantment(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/Enchantment;)Z"),
            require = 1, allow = 1)
    private static boolean bons$mahoutsukaiCoo49ProjectorPrecheck(ItemStack stack, Enchantment enchantment) {
        return MahouProjector.hasEnchantment(stack, enchantment);
    }
}
