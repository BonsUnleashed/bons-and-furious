package bons.furious.mixin.jei_search;

import java.util.List;
import mezz.jei.common.config.sorting.SortingConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * jei_sort_index_keys (JEI 19.51.0.418 for Minecraft 1.21.1 / NeoForge, tested build jei-1.21.1-neoforge-19.51.0.418.jar;
 * client): read access to a sorting config's loaded order (the list its stage comparator captured when
 * IngredientSorterComparators built the chain). Read only.
 *
 * Ported to 1.21.1: no change (SortingConfig decompiles identically in JEI 15.59.0.212 and 19.51.0.418; sorted is
 * still set only by load() and returned by getSorted(), whose result getComparator's lambda captures).
 */
@Mixin(value = SortingConfig.class, remap = false)
public interface SortingConfigAccessor {
    @Accessor(value = "sorted", remap = false)
    List<?> bons$sorted();
}
