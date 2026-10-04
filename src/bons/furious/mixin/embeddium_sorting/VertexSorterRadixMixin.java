package bons.furious.mixin.embeddium_sorting;

import bons.furious.patch.embeddium_sorting.QuadKeySort;
import me.jellysquid.mods.sodium.client.util.sorting.MergeSort;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * embeddium_entity_sort_radix (Embeddium 0.3.31+mc1.20.1 on Minecraft 1.20.1 / Forge 47.4.16, client only).
 *
 * Embeddium overwrites VertexSorting.byDistance(x, y, z) with VertexSorters.sortByDistance, whose sort(positions)
 * (AbstractVertexSorter.mergeSort) computes the squared distance of every quad centre and orders the quads with
 * MergeSort.mergeSort(keys): every translucent buffer sorted on upload goes through it (entity_translucent and the other
 * sortOnUpload render types, Oculus's batched entity segments). For keys without NaN that merge sort is the stable sort by
 * descending key. This redirect of that one call returns QuadKeySort.sort(keys) instead: the same permutation from a sort
 * of packed (key, index) longs (a stable byte-wise radix sort from QuadKeySort.RADIX_MIN), see QuadKeySort for why the
 * order is identical. Embeddium's MergeSort runs, called directly, for buffers shorter than QuadKeySort.MIN_LENGTH, when a
 * key is NaN, with the runtime switch off and in SHADOW mode (which returns Embeddium's result and only compares).
 * A redirect rather than a method wrapper: short buffers then cost one length test more than before and nothing else.
 * Embeddium is LGPL-3.0; nothing of its code is carried.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.util.sorting.VertexSorters$AbstractVertexSorter", remap = false)
public abstract class VertexSorterRadixMixin {
    @Redirect(method = "mergeSort([Lorg/joml/Vector3f;)[I",
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/util/sorting/MergeSort;mergeSort([F)[I"))
    private int[] bons$packedKeySort(float[] keys) {
        if (QuadKeySort.SHADOW) return QuadKeySort.shadow(keys, MergeSort.mergeSort(keys));
        if (keys.length >= QuadKeySort.MIN_LENGTH && QuadKeySort.enabled) {
            int[] order = QuadKeySort.sort(keys);
            if (order != null) return order;
        }
        return MergeSort.mergeSort(keys);
    }
}
