package bons.furious.mixin.jei_search;

import bons.furious.patch.jei_search.SortKeys;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Comparator;
import java.util.List;
import mezz.jei.gui.ingredients.IngredientSorter;
import mezz.jei.gui.ingredients.IngredientSorterComparators;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * jei_sort_index_keys (JEI 15.59.0.212, client only).
 *
 * IngredientSorter.sortIngredients builds the configured comparator chain (IngredientSorterComparators.getComparator,
 * unchanged: it loads and saves the sorting config files exactly as before) and sorts JEI's ingredient list with it. The
 * mod-name and ingredient-type stages compute List.indexOf over the sorted mod-name / type list for both elements of
 * every comparison. Here the chain the list is about to be sorted with is noted, and the sort computes each element's
 * keys once and sorts by them (SortKeys: same order, same list state); unsupported stages, other list classes, an
 * exception while computing keys, or the runtime switch off run the list's own sort. JEI is MIT; no JEI code is carried.
 */
@Mixin(value = IngredientSorter.class, remap = false)
public abstract class IngredientSorterMixin {
    @WrapOperation(method = "sortIngredients",
            at = @At(value = "INVOKE", target = "Lmezz/jei/gui/ingredients/IngredientSorterComparators;getComparator(Ljava/util/List;)Ljava/util/Comparator;"))
    private static Comparator<?> bons$noteChain(IngredientSorterComparators comparators, List<?> stages, Operation<Comparator<?>> original) {
        Comparator<?> chain = original.call(comparators, stages);
        if (SortKeys.enabled) SortKeys.note(comparators, stages, chain);
        return chain;
    }

    @WrapOperation(method = "sortIngredients", at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"))
    private static void bons$keyedSort(List<?> list, Comparator<?> comparator, Operation<Void> original) {
        if (!SortKeys.enabled) {
            original.call(list, comparator);
        } else if (SortKeys.SHADOW) {
            Object[] ours = SortKeys.order(list, comparator);
            original.call(list, comparator);
            SortKeys.shadow(list, ours);
        } else if (!SortKeys.sort(list, comparator)) {
            original.call(list, comparator);
        }
    }
}
