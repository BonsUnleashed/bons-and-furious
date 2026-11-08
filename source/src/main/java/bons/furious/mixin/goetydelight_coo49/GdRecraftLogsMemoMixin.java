package bons.furious.mixin.goetydelight_coo49;

import bons.furious.patch.goetydelight_coo49.GdLogs;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;

/**
 * goetydelight_log_items_memo (Goety's Delight 1.5.0-1.21.1, LGPL-2.1: no code carried; both sides).
 * RecraftBoatPlate.getLogItems streams every registered item per call (assemble, getResultItem: crafting, recipe book
 * indexing, recipe viewers); the list it builds is kept and handed out again while the item ids and the minecraft:logs
 * binding are the ones it was built under (GdLogs.logs: counters bumped by TagsUpdatedEvent / IdMappingEvent and the
 * tag's contents list by identity). The callers only read the list. Otherwise the original runs.
 */
@Mixin(targets = "net.v_black_cat.goetydelight.events.recipe.RecraftBoatPlate", remap = false)
public abstract class GdRecraftLogsMemoMixin {
    @SuppressWarnings("rawtypes")
    @WrapMethod(method = "getLogItems()Ljava/util/List;")
    private static List bons$goetydelightRecraftLogs(Operation<List> original) {
        return GdLogs.logs(GdLogs.RECRAFT, original);
    }
}
