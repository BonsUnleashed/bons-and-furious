package bons.furious.mixin.jei_fake_menus;

import bons.furious.patch.jei_fake_menus.FakeMenuRelease;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * jei_grindstone_menu_player_release (JEI 15.71.0.224 and 15.59.0.212), client only. [FIX]
 *
 * GrindstoneHelper.getFakeGrindstoneMenu stores the menu it builds in the static GRINDSTONE_MENU; that store is wrapped so
 * the helper (bons.furious.patch.jei_fake_menus.FakeMenuRelease) knows the menu and releases it with JEI's own
 * GrindstoneRecipeMaker.clearCache when it belongs to a replaced player. No JEI code is carried.
 */
@Mixin(targets = "mezz.jei.library.plugins.vanilla.grindstone.GrindstoneHelper", remap = false)
public abstract class GrindstoneMenuReleaseMixin {
    @WrapOperation(method = "getFakeGrindstoneMenu", at = @At(value = "FIELD",
            target = "Lmezz/jei/library/plugins/vanilla/grindstone/GrindstoneHelper;GRINDSTONE_MENU:Lnet/minecraft/world/inventory/GrindstoneMenu;",
            opcode = Opcodes.PUTSTATIC))
    private static void bons$menuCreated(GrindstoneMenu menu, Operation<Void> original) {
        original.call(menu);
        FakeMenuRelease.grindstoneCreated(menu);
    }
}
