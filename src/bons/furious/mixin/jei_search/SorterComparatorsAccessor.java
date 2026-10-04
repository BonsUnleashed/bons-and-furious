package bons.furious.mixin.jei_search;

import mezz.jei.gui.config.IngredientTypeSortingConfig;
import mezz.jei.gui.config.ModNameSortingConfig;
import mezz.jei.gui.ingredients.IngredientSorterComparators;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * jei_sort_index_keys (JEI 15.59.0.212, client): read access to the two sorting configs an IngredientSorterComparators
 * builds its mod-name and ingredient-type stages from. Read only.
 */
@Mixin(value = IngredientSorterComparators.class, remap = false)
public interface SorterComparatorsAccessor {
    @Accessor(value = "modNameSortingConfig", remap = false)
    ModNameSortingConfig bons$modNameSortingConfig();

    @Accessor(value = "ingredientTypeSortingConfig", remap = false)
    IngredientTypeSortingConfig bons$ingredientTypeSortingConfig();
}
