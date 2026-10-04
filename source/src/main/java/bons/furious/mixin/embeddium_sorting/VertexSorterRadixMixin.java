package bons.furious.mixin.embeddium_sorting;

import bons.furious.patch.embeddium_sorting.QuadKeySort;
import org.embeddedt.embeddium.impl.util.sorting.MergeSort;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * embeddium_entity_sort_radix (Embeddium 1.0.15+mc1.21.1 on Minecraft 1.21.1 / NeoForge 21.1.252, client only; first
 * written for Embeddium 0.3.31+mc1.20.1).
 *
 * Ported to 1.21.1: only names changed. Embeddium 1.0.15's VertexSorters / MergeSort / InsertionSort / AbstractSort are
 * the 0.3.31 code under org.embeddedt.embeddium.impl (CFR decompiles identical after the package rename; VertexSorting's
 * sort is now Mojang-named "sort", was m_277065_). On 1.21.1 the sorted buffers reach it through Embeddium's overwrite of
 * MeshData$SortState.buildSortedIndexBuffer (sorting.sort(centroids)) instead of BufferBuilder's sort state; the call
 * redirected here and its inputs are the same. Iris 1.8.12 refuses to load with Embeddium, so no shader segments.
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
@Mixin(targets = "org.embeddedt.embeddium.impl.util.sorting.VertexSorters$AbstractVertexSorter", remap = false)
public abstract class VertexSorterRadixMixin {
    @Redirect(method = "mergeSort([Lorg/joml/Vector3f;)[I",
            at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/util/sorting/MergeSort;mergeSort([F)[I"))
    private int[] bons$packedKeySort(float[] keys) {
        if (QuadKeySort.SHADOW) return QuadKeySort.shadow(keys, MergeSort.mergeSort(keys));
        if (keys.length >= QuadKeySort.MIN_LENGTH && QuadKeySort.enabled) {
            int[] order = QuadKeySort.sort(keys);
            if (order != null) return order;
        }
        return MergeSort.mergeSort(keys);
    }
}
