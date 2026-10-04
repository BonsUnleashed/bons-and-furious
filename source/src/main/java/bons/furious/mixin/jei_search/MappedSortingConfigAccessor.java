package bons.furious.mixin.jei_search;

import java.util.function.Function;
import mezz.jei.common.config.sorting.MappedSortingConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * jei_sort_index_keys (JEI 19.51.0.418 for Minecraft 1.21.1 / NeoForge, tested build jei-1.21.1-neoforge-19.51.0.418.jar;
 * client): read access to a mapped sorting config's mapping function (the one its stage comparator applies to both
 * elements of every comparison). Read only.
 *
 * Ported to 1.21.1: no change (MappedSortingConfig decompiles identically in JEI 15.59.0.212 and 19.51.0.418).
 */
@Mixin(value = MappedSortingConfig.class, remap = false)
public interface MappedSortingConfigAccessor {
    @Accessor(value = "mapping", remap = false)
    Function<Object, Object> bons$mapping();
}
