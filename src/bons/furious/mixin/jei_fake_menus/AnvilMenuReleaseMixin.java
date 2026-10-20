package bons.furious.mixin.jei_fake_menus;

import bons.furious.patch.jei_fake_menus.FakeMenuRelease;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.library.plugins.vanilla.anvil.AnvilHelper;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * jei_anvil_menu_player_release (JEI 15.71.0.224 and 15.59.0.212), client only. [FIX]
 *
 * AnvilHelper.getFakeAnvilMenu stores the menu it builds in the static ANVIL_MENU; that store is wrapped so the helper
 * (bons.furious.patch.jei_fake_menus.FakeMenuRelease) knows the menu and listens for player changes, and releases it with
 * JEI's own clearCache when it belongs to a replaced player. findLevelsCost is wrapped for the shadow check only (the
 * method runs unchanged). No JEI code is carried.
 */
@Mixin(value = AnvilHelper.class, remap = false)
public abstract class AnvilMenuReleaseMixin {
    @WrapOperation(method = "getFakeAnvilMenu", at = @At(value = "FIELD",
            target = "Lmezz/jei/library/plugins/vanilla/anvil/AnvilHelper;ANVIL_MENU:Lnet/minecraft/world/inventory/AnvilMenu;", opcode = Opcodes.PUTSTATIC))
    private static void bons$menuCreated(AnvilMenu menu, Operation<Void> original) {
        original.call(menu);
        FakeMenuRelease.anvilCreated(menu);
    }

    @WrapMethod(method = "findLevelsCost")
    private static int bons$shadowCost(ItemStack left, ItemStack right, Operation<Integer> original) {
        int cost = original.call(left, right);
        return FakeMenuRelease.SHADOW ? FakeMenuRelease.shadowCost(left, right, cost) : cost;
    }
}
