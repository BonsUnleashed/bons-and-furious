package bons.furious.mixin.jei_startup;

import bons.furious.patch.jei_startup.HiddenMenuSync;
import mezz.jei.library.plugins.vanilla.anvil.AnvilHelper;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AnvilHelper.class, remap = false)
public abstract class AnvilSimulationMixin {
    @Inject(method = "getFakeAnvilMenu", at = @At("RETURN"))
    private static void bons$mark(CallbackInfoReturnable<AnvilMenu> callback) {
        AnvilMenu menu = callback.getReturnValue();
        if (menu != null && menu.getClass() == AnvilMenu.class) HiddenMenuSync.mark(menu);
    }
}
