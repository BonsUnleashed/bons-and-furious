package bons.furious.mixin.dh_loader;

import bons.furious.patch.dh_loader.QuadSortKeys;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.BufferQuad;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_quad_sort_keys (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge, LGPL-3.0; client).
 *
 * LodQuadBuilder.mergeQuadsInternal's list.sort(comparator) (DH's lambda q1.compare(q2, mergeDirection)) goes through
 * {@link QuadSortKeys#sort}, which produces the same stable order from the quads' own sort keys sorted as primitive values
 * and makes the original call (DH's sort with DH's comparator) whenever its premises do not hold. A plain redirect: no
 * captured locals (MixinExtras would pass those through allocated reference objects); which key the comparator compares
 * is read from the comparator itself (two probe quads in QuadSortKeys.sort). No other mod mixes into this class; no DH code
 * is carried.
 *
 * Ported to 1.21.1: no change (LodQuadBuilder and BufferQuad are byte-identical in DH 3.3.3; one ArrayList.sort call).
 */
@Mixin(value = LodQuadBuilder.class, remap = false)
public abstract class QuadSortKeysMixin {
    @Redirect(method = "mergeQuadsInternal", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;sort(Ljava/util/Comparator;)V"))
    private static void bons$sortByKeys(ArrayList<BufferQuad> list, Comparator<? super BufferQuad> comparator) {
        QuadSortKeys.sort(list, comparator);
    }
}
