package bons.furious.mixin.dh_loader;

import bons.furious.patch.dh_loader.AdjacentFaceSkip;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnBox;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * distanthorizons_adjacent_face_skip (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge, LGPL-3.0; client).
 *
 * In ColumnBox.makeAdjVerticalQuad (the side faces of one LOD box), two values DH reads go through
 * {@link AdjacentFaceSkip}:
 *  - the loop bound adjCount = adjColumnView.size (the second GETFIELD of size; the first is the empty-view check before the
 *    loop) stops right after the adjacent column's last non-empty slot ({@link AdjacentFaceSkip#bound}): the trailing empty
 *    slots are skipped by DH's loop anyway;
 *  - the loop's read of the current adjacent point, adjColumnView.get(adjIndex) (ordinal 1; ordinal 0 is the column's
 *    first-point check before the loop, 2 and 3 read the points above and below): a point that lies wholly at or below the
 *    face's bottom and whose sky-light gap above also ends there becomes the empty data point, which DH's loop skips; its
 *    two light-range calls would only have copied the segment list ({@link AdjacentFaceSkip#point}).
 * Captured (MixinExtras passes them as plain values): the adjacent column view (the only ColumnRenderView argument), the
 * loop index (local 22), the face's bottom yMin (2nd short argument) and the current segment list (local 16, read only for
 * the shadow check). No other mod mixes into this class; no DH code is carried.
 *
 * Ported to 1.21.1: no change (ColumnBox is byte-identical in DH 3.3.3: same anchors, ordinals and local slots).
 */
@Mixin(value = ColumnBox.class, remap = false)
public abstract class AdjacentFaceSkipMixin {
    @ModifyExpressionValue(method = "makeAdjVerticalQuad", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 1,
            target = "Lcom/seibel/distanthorizons/core/dataObjects/render/columnViews/ColumnRenderView;size:I"))
    private static int bons$boundAtLastPoint(int size, @Local(argsOnly = true) ColumnRenderView view) {
        return AdjacentFaceSkip.bound(size, view);
    }

    @ModifyExpressionValue(method = "makeAdjVerticalQuad", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/seibel/distanthorizons/core/dataObjects/render/columnViews/ColumnRenderView;get(I)J"))
    private static long bons$skipPointsBelowFace(long point, @Local(argsOnly = true) ColumnRenderView view, @Local(index = 22) int index,
                                                 @Local(argsOnly = true, ordinal = 1) short yMin, @Local(index = 16) LongArrayList segments) {
        return AdjacentFaceSkip.point(point, view, index, yMin, segments);
    }
}
