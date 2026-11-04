package bons.furious.mixin.justdirethings_coo49;

import bons.furious.patch.justdirethings_coo49.AreaPreviews;
import com.direwolf20.justdirethings.client.events.RenderLevelLast;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * justdirethings_area_preview_no_copy (Just Dire Things [Forge] 1.1.11, client side; no Just Dire Things code is carried).
 * RenderLevelLast.renderAreaPreviews copies each of the 169 nearby chunks' block entity values into a new ArrayList every
 * frame before walking them; the wrapped constructor hands back a list that walks the live values instead (the loop never
 * changes the map, and the map changes only on this thread). Same elements, same order. See AreaPreviews.
 */
@Mixin(value = RenderLevelLast.class, remap = false)
public abstract class AreaPreviewNoCopyMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "renderAreaPreviews", at = @At(value = "NEW", target = "(Ljava/util/Collection;)Ljava/util/ArrayList;"), require = 1, allow = 1)
    private static ArrayList bons$justdirethingsAreaPreviewNoCopy(Collection values, Operation<ArrayList> original) {
        return AreaPreviews.copyFree(values, original);
    }
}
