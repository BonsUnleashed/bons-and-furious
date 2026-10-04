package bons.furious.mixin.jei_search;

import java.util.function.Function;
import mezz.jei.common.config.sorting.MappedSortingConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * jei_sort_index_keys (JEI 15.59.0.212, client): read access to a mapped sorting config's mapping function (the one its
 * stage comparator applies to both elements of every comparison). Read only.
 */
@Mixin(value = MappedSortingConfig.class, remap = false)
public interface MappedSortingConfigAccessor {
    @Accessor(value = "mapping", remap = false)
    Function<Object, Object> bons$mapping();
}
