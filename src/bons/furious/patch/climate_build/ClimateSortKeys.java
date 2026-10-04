package bons.furious.patch.climate_build;

import bons.furious.mixin.climate_build.ClimateNodeSpaceAccessor;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.biome.Climate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_climate_tree_sort_keys (Minecraft 1.20.1, both sides). No Minecraft code here.
 *
 * Building a climate search tree (Climate.RTree.create: TerraBlender builds one per region when a server starts,
 * Blueprint's modded biome provider one per codec decode at registry load) sorts the nodes of every level once per
 * dimension and once more for the chosen split, each time with a chain of seven key comparators (dimension j first, then
 * j+1, ... mod 7). Every comparison recomputes each key it needs from the node's Parameter: the centre (min + max) / 2 of
 * that dimension's range, its absolute value in the final sort. That key extraction is most of the build
 * (RTree.sort: 1.46% of all srv10_jfr1 boot samples, 0.51% of cli10_jfr1's load window).
 *
 * Here the keys are computed once per node and sort (same arithmetic, same 64-bit overflow), stored row by row in one
 * long array in chain order, and the node positions are sorted by a stable merge sort that compares rows exactly as the
 * chain does (Long.compare of the first dimension whose keys differ, else equal). The chain is a consistent total
 * preorder on fixed keys and the list's own sort (TimSort, or the legacy merge sort when requested) is stable, and a
 * stable sort of a consistent preorder has exactly one result: elements in key order, equal ones in their original
 * order. So the nodes end in the same order; they are written back into the list (for Arrays.asList views that is the
 * subtree's own child array, which the original sorts in place too). A null node, a parameter space shorter than the
 * dimension count or a null parameter (where the original comparator would throw part-way through) take the original
 * sort, as does any list that is not random-access.
 */
public final class ClimateSortKeys {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.vanillaClimateTreeSortKeys=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaClimateTreeSortKeys", "true"));
    /** Counters (read by probes): sorts done with keys, nodes sorted, sorts left to the original. */
    public static final AtomicLong SORTS = new AtomicLong(), NODES = new AtomicLong(), FALLBACKS = new AtomicLong();

    private static final int INSERTION = 24;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private ClimateSortKeys() {
    }

    /**
     * RTree.sort(list, dims, first, abs): true when the list was sorted here, false when the caller must run the original
     * (nothing has been changed then).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean sort(List list, int dims, int first, boolean abs) {
        int n = list.size();
        if (!(list instanceof RandomAccess) || dims < 1 || first < 0 || first >= dims) return fallback();
        Object[] nodes = new Object[n];
        long[] keys = new long[n * dims];
        for (int i = 0; i < n; i++) {
            Object node = list.get(i);
            if (!(node instanceof ClimateNodeSpaceAccessor a)) return fallback();
            Climate.Parameter[] space = a.bons$parameterSpace();
            if (space == null || space.length < dims) return fallback();
            int row = i * dims;
            for (int k = 0; k < dims; k++) {
                Climate.Parameter p = space[(first + k) % dims];
                if (p == null) return fallback();
                long centre = (p.f_186813_() + p.f_186814_()) / 2L;
                keys[row + k] = abs ? Math.abs(centre) : centre;
            }
            nodes[i] = node;
        }
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        if (n > 1) mergeSort(order, new int[n], 0, n, keys, dims);
        for (int i = 0; i < n; i++) list.set(i, nodes[order[i]]);
        SORTS.incrementAndGet();
        NODES.addAndGet(n);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_climate_tree_sort_keys sorts climate search tree nodes by precomputed keys");
        }
        return true;
    }

    /** Row a vs row b in chain order: Long.compare of the first differing key, else 0 (what the comparator chain returns). */
    private static int compare(long[] keys, int dims, int a, int b) {
        int ra = a * dims, rb = b * dims;
        for (int k = 0; k < dims; k++) {
            long x = keys[ra + k], y = keys[rb + k];
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }

    /** Stable: equal rows keep their order (insertion moves only past strictly greater rows; merges take the left run on ties). */
    private static void mergeSort(int[] a, int[] tmp, int lo, int hi, long[] keys, int dims) {
        if (hi - lo <= INSERTION) {
            for (int i = lo + 1; i < hi; i++) {
                int v = a[i];
                int j = i - 1;
                while (j >= lo && compare(keys, dims, a[j], v) > 0) {
                    a[j + 1] = a[j];
                    j--;
                }
                a[j + 1] = v;
            }
            return;
        }
        int mid = (lo + hi) >>> 1;
        mergeSort(a, tmp, lo, mid, keys, dims);
        mergeSort(a, tmp, mid, hi, keys, dims);
        if (compare(keys, dims, a[mid - 1], a[mid]) <= 0) return;   // already in order
        System.arraycopy(a, lo, tmp, lo, hi - lo);
        int i = lo, j = mid, k = lo;
        while (i < mid && j < hi) a[k++] = compare(keys, dims, tmp[j], tmp[i]) < 0 ? tmp[j++] : tmp[i++];
        while (i < mid) a[k++] = tmp[i++];
        while (j < hi) a[k++] = tmp[j++];
    }

    private static boolean fallback() {
        FALLBACKS.incrementAndGet();
        return false;
    }
}
