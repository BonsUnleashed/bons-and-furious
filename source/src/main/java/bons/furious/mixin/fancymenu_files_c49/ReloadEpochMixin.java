package bons.furious.mixin.fancymenu_files_c49;

import bons.furious.patch.fancymenu_files_c49.LocalSourceMemo;
import de.keksuccino.fancymenu.customization.ScreenCustomization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fancymenu_local_source_memo (FancyMenu 3.9.14 for NeoForge 1.21.1; client side; trade-off, off by default): at the start of
 * ScreenCustomization.reloadFancyMenu every kept local-source result is dropped (LocalSourceMemo's epoch), so the layouts
 * the reload builds resolve their sources against the disk again.
 */
@Mixin(value = ScreenCustomization.class, remap = false)
public abstract class ReloadEpochMixin {
    @Inject(method = "reloadFancyMenu()V", at = @At("HEAD"), require = 1, allow = 1)
    private static void bons$fancymenuFilesC49Reloaded(CallbackInfo ci) {
        LocalSourceMemo.reloaded();
    }
}
