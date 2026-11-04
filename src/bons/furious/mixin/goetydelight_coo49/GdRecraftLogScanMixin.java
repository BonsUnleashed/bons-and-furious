package bons.furious.mixin.goetydelight_coo49;

import bons.furious.patch.goetydelight_coo49.GdLogs;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;

/**
 * goetydelight_log_scan_lean (Goety's Delight 1.5.2, LGPL-2.1: no code carried; both sides). The log scan's test
 * (lambda$getLogItems$0: item.getDefaultInstance().is(ItemTags.LOGS)) asks the item's own registry holder for the tag
 * when the item's class inherits Item's getDefaultInstance and the few methods the ItemStack constructor runs (GdLogs.isLog):
 * the same answer without one throwaway ItemStack per registered item. Other items run the original test.
 */
@Mixin(targets = "net.v_black_cat.goetydelight.recipe.RecraftBoatPlate", remap = false)
public abstract class GdRecraftLogScanMixin {
    @WrapMethod(method = "lambda$getLogItems$0(Lnet/minecraft/world/item/Item;)Z")
    private static boolean bons$goetydelightRecraftLogTest(Item item, Operation<Boolean> original) {
        return GdLogs.isLog(item, original);
    }
}
