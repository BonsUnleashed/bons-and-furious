package bons.furious.mixin.goetydelight_coo49;

import bons.furious.patch.goetydelight_coo49.GdLogs;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;

/**
 * goetydelight_log_scan_lean (Goety's Delight 1.5.2, LGPL-2.1: no code carried; client). The same lean test as
 * GdRecraftLogScanMixin for CraftingDisplayHandler's copy of the log scan (lambda$getLogItems$0). See GdLogs.isLog.
 */
@Mixin(targets = "net.v_black_cat.goetydelight.init.CraftingDisplayHandler", remap = false)
public abstract class GdDisplayLogScanMixin {
    @WrapMethod(method = "lambda$getLogItems$0(Lnet/minecraft/world/item/Item;)Z")
    private static boolean bons$goetydelightDisplayLogTest(Item item, Operation<Boolean> original) {
        return GdLogs.isLog(item, original);
    }
}
