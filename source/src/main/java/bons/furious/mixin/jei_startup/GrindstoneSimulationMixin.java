package bons.furious.mixin.jei_startup;

import bons.furious.patch.jei_startup.HiddenMenuSync;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "mezz.jei.library.plugins.vanilla.grindstone.GrindstoneHelper", remap = false)
public abstract class GrindstoneSimulationMixin {
    @Inject(method = "getFakeGrindstoneMenu", at = @At("RETURN"))
    private static void bons$mark(CallbackInfoReturnable<GrindstoneMenu> callback) {
        GrindstoneMenu menu = callback.getReturnValue();
        if (menu != null && menu.getClass() == GrindstoneMenu.class) HiddenMenuSync.mark(menu);
    }
}
