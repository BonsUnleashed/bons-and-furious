package bons.furious.patch.jei_search;

import bons.furious.mixin.jei_search.MappedSortingConfigAccessor;
import bons.furious.mixin.jei_search.SortingConfigAccessor;
import bons.furious.mixin.jei_search.SorterComparatorsAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import mezz.jei.common.config.IngredientSortStage;
import mezz.jei.gui.ingredients.IListElementInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch jei_sort_index_keys (JEI 15.59.0.212, client). No JEI code here.
 *
 * IngredientSorter.sortIngredients sorts JEI's ingredient list (about 40,000 entries here, once per world join and on
 * every rebuild) with the chain IngredientSorterComparators.getComparator(stages) returns: stage comparators joined
 * with thenComparing (an empty stage list means getDefault(): mod name, ingredient type, creative menu). The stage
 * comparators are Comparator.comparingInt over
 *  - MOD_NAME / INGREDIENT_TYPE: indexOfSort(sorted.indexOf(mapping.apply(info))) of the mod-name / type sorting config
 *    (sorted = the config's loaded order, mapping = IListElementInfo::getModNameForSorting resp.
 *    IngredientTypeSortingConfig::getIngredientTypeString; indexOfSort maps -1 to Integer.MAX_VALUE), recomputed for both
 *    elements of every comparison: a linear List.indexOf over ~470 mod names, ~1.3 million times per sort (the sort is
 *    4.0% of our test rig's filter window);
 *  - CREATIVE_MENU: info.getCreatedIndex();
 * and ALPHABETICAL is Comparator.comparing(info -> info.getNames().get(0)) (String.compareTo).
 *
 * Here each element's keys are computed once with the same functions on the same objects (the mapping functions and
 * the ingredient types' getIngredientClass are pure: a census of the pack's ingredient types found only class-literal
 * returns), and the elements are sorted by a stable merge sort that compares rows exactly as the chain does. The chain
 * is a consistent total preorder on fixed keys and ArrayList.sort is stable, and a stable sort of a consistent preorder
 * has exactly one result, so the list ends in the same order. It is written back with ArrayList.replaceAll, which, like
 * ArrayList.sort, stores the elements into the same array and increments modCount once.
 *
 * Declines (the list's own sort runs on the untouched list): the comparator is not the chain just built on this thread,
 * the list is not exactly java.util.ArrayList, a stage other than MOD_NAME / INGREDIENT_TYPE / CREATIVE_MENU /
 * ALPHABETICAL, a config whose order is not loaded, a null alphabetical key, or any exception while computing keys (the
 * original then fails the same way).
 */
public final class SortKeys {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.jeiSortIndexKeys=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.jeiSortIndexKeys", "true"));
    /** Shadow mode for rigs: the keyed order is computed, the list's own sort runs, and the two orders are compared. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.jeiSortIndexKeys.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters (read by probes): sorts done here, elements sorted, sorts left to the list's own sort. */
    public static final AtomicLong SORTS = new AtomicLong(), ELEMENTS = new AtomicLong(), FALLBACKS = new AtomicLong();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final IngredientSortStage[] DEFAULT = {IngredientSortStage.MOD_NAME, IngredientSortStage.INGREDIENT_TYPE, IngredientSortStage.CREATIVE_MENU};
    private static final int INSERTION = 24;
    private static volatile boolean announced;
    private static volatile Pending pending;

    private SortKeys() {
    }

    private record Pending(Thread thread, Object comparators, Object[] stages, Object chain) {
    }

    /** IngredientSorterComparators.getComparator(stages) just returned chain on this thread. */
    public static void note(Object comparators, List<?> stages, Object chain) {
        pending = new Pending(Thread.currentThread(), comparators, stages.toArray(), chain);
    }

    /** list.sort(comparator) inside sortIngredients: true when sorted here, false when the caller must run the list's own sort. */
    public static boolean sort(List<?> list, Comparator<?> comparator) {
        Object[] order = order(list, comparator);
        if (order == null) {
            FALLBACKS.incrementAndGet();
            return false;
        }
        @SuppressWarnings("unchecked")
        ArrayList<Object> a = (ArrayList<Object>) list;
        a.replaceAll(new UnaryOperator<>() {
            private int next;

            @Override
            public Object apply(Object o) {
                return order[next++];
            }
        });
        SORTS.incrementAndGet();
        ELEMENTS.addAndGet(order.length);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: jei_sort_index_keys sorts JEI's ingredient list by keys computed once per ingredient ({} ingredients)", order.length);
        }
        return true;
    }

    /** The order the chain gives the list, or null where this declines. Consumes the noted chain. */
    public static Object[] order(List<?> list, Comparator<?> comparator) {
        Pending p = pending;
        pending = null;
        if (p == null || p.thread != Thread.currentThread() || p.chain != comparator || list.getClass() != ArrayList.class) return null;
        Object[] stages = p.stages.length == 0 ? DEFAULT : p.stages;
        int s = stages.length, n = list.size();
        Object[] elems = list.toArray();
        int[] ik = new int[n * s];
        String[] sk = null;
        boolean[] alpha = new boolean[s];
        SorterComparatorsAccessor comparators = (SorterComparatorsAccessor) p.comparators;
        try {
            for (int j = 0; j < s; j++) {
                Object stage = stages[j];
                if (stage == IngredientSortStage.MOD_NAME || stage == IngredientSortStage.INGREDIENT_TYPE) {
                    Object cfg = stage == IngredientSortStage.MOD_NAME ? comparators.bons$modNameSortingConfig() : comparators.bons$ingredientTypeSortingConfig();
                    List<?> sorted = ((SortingConfigAccessor) cfg).bons$sorted();
                    Function<Object, Object> mapping = ((MappedSortingConfigAccessor) cfg).bons$mapping();
                    if (sorted == null || mapping == null) return null;
                    for (int i = 0; i < n; i++) {
                        int idx = sorted.indexOf(mapping.apply(elems[i]));
                        ik[i * s + j] = idx < 0 ? Integer.MAX_VALUE : idx;
                    }
                } else if (stage == IngredientSortStage.CREATIVE_MENU) {
                    for (int i = 0; i < n; i++) ik[i * s + j] = ((IListElementInfo<?>) elems[i]).getCreatedIndex();
                } else if (stage == IngredientSortStage.ALPHABETICAL) {
                    if (sk == null) sk = new String[n * s];
                    alpha[j] = true;
                    for (int i = 0; i < n; i++) {
                        String name = ((IListElementInfo<?>) elems[i]).getNames().get(0);
                        if (name == null) return null;
                        sk[i * s + j] = name;
                    }
                } else {
                    return null;
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        if (n > 1) mergeSort(idx, new int[n], 0, n, ik, sk, alpha, s);
        Object[] out = new Object[n];
        for (int i = 0; i < n; i++) out[i] = elems[idx[i]];
        return out;
    }

    /** Shadow mode: the list after its own sort vs the keyed order (null = declined, not counted). */
    public static void shadow(List<?> sortedList, Object[] ours) {
        if (ours == null) return;
        SHADOW_CHECKS.incrementAndGet();
        boolean same = ours.length == sortedList.size();
        for (int i = 0; same && i < ours.length; i++) same = ours[i] == sortedList.get(i);
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: jei_sort_index_keys shadow mismatch: keyed order differs from the list's own sort ({} elements)", ours.length);
    }

    private static int cmp(int a, int b, int[] ik, String[] sk, boolean[] alpha, int s) {
        int ra = a * s, rb = b * s;
        for (int j = 0; j < s; j++) {
            int c = alpha[j] ? sk[ra + j].compareTo(sk[rb + j]) : Integer.compare(ik[ra + j], ik[rb + j]);
            if (c != 0) return c;
        }
        return 0;
    }

    /** Stable merge sort of positions [lo, hi) by row (insertion sort below INSERTION). */
    private static void mergeSort(int[] a, int[] tmp, int lo, int hi, int[] ik, String[] sk, boolean[] alpha, int s) {
        if (hi - lo <= INSERTION) {
            for (int i = lo + 1; i < hi; i++) {
                int v = a[i], j = i - 1;
                while (j >= lo && cmp(a[j], v, ik, sk, alpha, s) > 0) {
                    a[j + 1] = a[j];
                    j--;
                }
                a[j + 1] = v;
            }
            return;
        }
        int mid = (lo + hi) >>> 1;
        mergeSort(a, tmp, lo, mid, ik, sk, alpha, s);
        mergeSort(a, tmp, mid, hi, ik, sk, alpha, s);
        if (cmp(a[mid - 1], a[mid], ik, sk, alpha, s) <= 0) return;
        System.arraycopy(a, lo, tmp, lo, hi - lo);
        int i = lo, j = mid, k = lo;
        while (i < mid && j < hi) a[k++] = cmp(tmp[j], tmp[i], ik, sk, alpha, s) < 0 ? tmp[j++] : tmp[i++];
        while (i < mid) a[k++] = tmp[i++];
        while (j < hi) a[k++] = tmp[j++];
    }
}
