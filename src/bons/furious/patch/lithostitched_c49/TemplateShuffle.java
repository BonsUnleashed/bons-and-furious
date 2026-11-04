package bons.furious.patch.lithostitched_c49;

import bons.furious.mixin.lithostitched_c49.WeightedEntryAccessor;
import dev.worldgen.lithostitched.worldgen.poolelement.DelegatingPoolElement;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lithostitched_template_shuffle_arrays (Lithostitched 1.4.11, MIT; server side incl. the integrated
 * server). Ours: the shuffle is rewritten from the formula, no Lithostitched code is copied.
 *
 * What Lithostitched does. LithostitchedTemplates.shuffle runs for every jigsaw connector its jigsaw generator places
 * (with breaks_seed_parity on: every minecraft:jigsaw structure): it copies every weighted entry through a stream (a new
 * entry object each, which re-reads the element's prioritized flag), makes an ArrayList of them, draws one nextFloat per
 * entry in index order into the key -pow(r, 1 / weight) (minus 2 for a prioritized element), sorts the list by that key with
 * Comparator.comparingDouble (stable TimSort), and maps it through a second stream to an unmodifiable List.
 *
 * What the switch does. The same steps on plain arrays: every element and weight read in index order (the prioritized flag
 * asked of the element exactly as the copy constructor asks it), then one nextFloat per entry in index order, the same key
 * expression with the same float/double rounding, a stable sort of the indices by Double.compare (the comparator
 * comparingDouble uses; ties keep index order as TimSort does), and the elements returned through Stream.toList() of an
 * array (ReferencePipeline.toList: the same unmodifiable list class as the stock pipeline).
 *
 * Why the result is identical: same RNG calls in the same order (so the RandomSource ends in the same state), keys equal
 * bit for bit, the same total order with the same tie rule, the same list class and content.
 *
 * -Dbons_and_furious.lithostitchedTemplateShuffleArrays=false switches it off at run time. No shadow mode: the shuffle draws
 * from the caller's RandomSource, which cannot be replayed in game; the offline proof compares both on twin RNGs.
 */
public final class TemplateShuffle {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lithostitchedTemplateShuffleArrays", "true"));
    private static volatile boolean announced;

    private TemplateShuffle() {
    }

    /** In place of LithostitchedTemplates.shuffle(random), for its entries list. */
    @SuppressWarnings("unchecked")
    public static List<StructurePoolElement> shuffle(List<?> entries, RandomSource random) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: lithostitched_template_shuffle_arrays applies (Lithostitched's template pool shuffle uses arrays instead of streams)");
        }
        int n = entries.size();
        StructurePoolElement[] element = new StructurePoolElement[n];
        int[] weight = new int[n];
        boolean[] prioritized = new boolean[n];
        for (int i = 0; i < n; i++) {
            WeightedEntryAccessor e = (WeightedEntryAccessor) entries.get(i);
            StructurePoolElement el = e.bons$lsElement();
            element[i] = el;
            weight[i] = e.bons$lsWeight();
            prioritized[i] = el instanceof DelegatingPoolElement delegating && delegating.prioritized();
        }
        double[] key = new double[n];
        for (int i = 0; i < n; i++) {
            float value = random.m_188501_();
            key[i] = -Math.pow(value, 1.0f / (float) weight[i]) + (double) (prioritized[i] ? -2 : 0);
        }
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        if (n > 1) sort(order, key, n);
        StructurePoolElement[] out = new StructurePoolElement[n];
        for (int i = 0; i < n; i++) out[i] = element[order[i]];
        return Stream.of(out).toList();
    }

    /** Stable ascending sort of the indices by Double.compare of their keys (ties keep the index order). */
    static void sort(int[] a, double[] key, int n) {
        if (n <= 32) {
            for (int i = 1; i < n; i++) {
                int v = a[i];
                double kv = key[v];
                int lo = 0, hi = i;                       // first position whose key is greater than kv (after equals)
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if (Double.compare(kv, key[a[mid]]) < 0) hi = mid;
                    else lo = mid + 1;
                }
                System.arraycopy(a, lo, a, lo + 1, i - lo);
                a[lo] = v;
            }
            return;
        }
        int[] tmp = new int[n];
        mergeSort(a, tmp, key, 0, n);
    }

    private static void mergeSort(int[] a, int[] tmp, double[] key, int from, int to) {
        if (to - from <= 32) {
            for (int i = from + 1; i < to; i++) {
                int v = a[i];
                double kv = key[v];
                int j = i - 1;
                while (j >= from && Double.compare(kv, key[a[j]]) < 0) {
                    a[j + 1] = a[j];
                    j--;
                }
                a[j + 1] = v;
            }
            return;
        }
        int mid = (from + to) >>> 1;
        mergeSort(a, tmp, key, from, mid);
        mergeSort(a, tmp, key, mid, to);
        if (Double.compare(key[a[mid - 1]], key[a[mid]]) <= 0) return;
        System.arraycopy(a, from, tmp, from, to - from);
        int i = from, j = mid, k = from;
        while (i < mid && j < to) a[k++] = Double.compare(key[tmp[j]], key[tmp[i]]) < 0 ? tmp[j++] : tmp[i++];
        while (i < mid) a[k++] = tmp[i++];
        while (j < to) a[k++] = tmp[j++];
    }
}
