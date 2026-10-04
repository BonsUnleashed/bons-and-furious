package bons.furious.mixin.storagedrawers;

import bons.furious.patch.storagedrawers.CountLabels;
import com.jaquadro.minecraft.storagedrawers.util.CountFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * storagedrawers_count_label_memo (Storage Drawers 12.15.1, MIT; client): every String.format call of
 * CountFormatter.formatApprox (the counts from 10,000 up) goes through CountLabels, which hands back the text String.format
 * produced for the same format, value and locale on the render thread (see there). No Storage Drawers code is carried.
 */
@Mixin(value = CountFormatter.class, remap = false)
public abstract class CountFormatterMixin {
    @Redirect(method = "formatApprox", at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private static String bons$countText(String format, Object[] args) {
        return CountLabels.format(format, args);
    }
}
