package bons.furious.mixin.goetydelight_coo49;

import bons.furious.patch.goetydelight_coo49.GdLogs;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;

/**
 * goetydelight_log_items_memo (Goety's Delight 1.5.2, LGPL-2.1: no code carried; client). CraftingDisplayHandler.getLogItems
 * (every 100 client ticks while a crafting table holding only a boat plate is open) streams every registered item; the
 * list is kept as in GdRecraftLogsMemoMixin (GdLogs.logs, its own slot). The caller only reads the list.
 */
@Mixin(targets = "net.v_black_cat.goetydelight.init.CraftingDisplayHandler", remap = false)
public abstract class GdDisplayLogsMemoMixin {
    @SuppressWarnings("rawtypes")
    @WrapMethod(method = "getLogItems()Ljava/util/List;")
    private static List bons$goetydelightDisplayLogs(Operation<List> original) {
        return GdLogs.logs(GdLogs.DISPLAY, original);
    }
}
