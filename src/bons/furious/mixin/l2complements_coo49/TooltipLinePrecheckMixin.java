package bons.furious.mixin.l2complements_coo49;

import bons.furious.patch.l2complements_coo49.TooltipPrecheck;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * l2complements_tooltip_line_precheck (L2 Complements 2.6.1, client): @Redirect of the two calls of the first test in
 * ClientEventHandler.modifyItemTooltip ("if the stack is neither enchanted nor an enchanted book, return"). Each handler
 * makes the shipped call and, only when it answers true, also asks TooltipPrecheck whether the handler could change the
 * tooltip at all; when it cannot, the test answers false and the shipped code returns at once, before reading the
 * enchantment map. Tooltips of other items pay nothing but the redirected call. The handlers capture the target method's
 * ItemTooltipEvent argument. Our own logic only (All Rights Reserved target; none of its code is carried).
 */
@Mixin(targets = "dev.xkmc.l2complements.events.ClientEventHandler", remap = false)
public abstract class TooltipLinePrecheckMixin {
    private static final String METHOD = "modifyItemTooltip(Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;)V";

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41793_()Z", ordinal = 0), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49TooltipEnchanted(ItemStack stack, ItemTooltipEvent event) {
        return stack.m_41793_() && TooltipPrecheck.mayChange(event);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z", ordinal = 0),
            require = 1, allow = 1)
    private static boolean bons$l2complements_coo49TooltipBook(ItemStack stack, Item item, ItemTooltipEvent event) {
        return stack.m_150930_(item) && TooltipPrecheck.mayChange(event);
    }
}
