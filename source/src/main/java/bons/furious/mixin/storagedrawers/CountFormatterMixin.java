package bons.furious.mixin.storagedrawers;

import bons.furious.patch.storagedrawers.CountLabels;
import com.jaquadro.minecraft.storagedrawers.util.CountFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * storagedrawers_count_label_memo (Storage Drawers 13.11.4 for NeoForge 1.21.1, MIT; client): every String.format call of
 * CountFormatter.formatApprox (the counts from 10,000 up) goes through CountLabels, which hands back the text String.format
 * produced for the same format, value and locale on the render thread (see there). Ported to 1.21.1: the five calls moved
 * into the new overload formatApprox(Font, int), named here by its descriptor. No Storage Drawers code is carried.
 */
@Mixin(value = CountFormatter.class, remap = false)
public abstract class CountFormatterMixin {
    @Redirect(method = "formatApprox(Lnet/minecraft/client/gui/Font;I)Ljava/lang/String;",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private static String bons$countText(String format, Object[] args) {
        return CountLabels.format(format, args);
    }
}
