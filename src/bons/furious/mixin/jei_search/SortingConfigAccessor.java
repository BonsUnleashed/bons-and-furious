package bons.furious.mixin.jei_search;

import java.util.List;
import mezz.jei.common.config.sorting.SortingConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * jei_sort_index_keys (JEI 15.59.0.212, client): read access to a sorting config's loaded order (the list its stage
 * comparator captured when IngredientSorterComparators built the chain). Read only.
 */
@Mixin(value = SortingConfig.class, remap = false)
public interface SortingConfigAccessor {
    @Accessor(value = "sorted", remap = false)
    List<?> bons$sorted();
}
