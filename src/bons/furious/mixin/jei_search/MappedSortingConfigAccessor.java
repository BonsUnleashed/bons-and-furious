package bons.furious.mixin.jei_search;

import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * jei_sort_index_keys (JEI 15.59.0.212, client): read access to a mapped sorting config's mapping function (the one its
 * stage comparator applies to both elements of every comparison). Read only. Since 1.0.46 the target is named as a
 * string: JEI 15.62+ (the tested 15.71.0.224) no longer has this class, where the switch stands down (redundant_since), and
 * the group must compile against either JEI build.
 */
@Mixin(targets = "mezz.jei.common.config.sorting.MappedSortingConfig", remap = false)
public interface MappedSortingConfigAccessor {
    @Accessor(value = "mapping", remap = false)
    Function<Object, Object> bons$mapping();
}
