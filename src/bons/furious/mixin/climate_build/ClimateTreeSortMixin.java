package bons.furious.mixin.climate_build;

import bons.furious.patch.climate_build.ClimateSortKeys;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_climate_tree_sort_keys (Minecraft 1.20.1 world generation setup, both sides).
 *
 * Climate.RTree.sort(nodes, dims, first, abs) sorts a level's nodes with a chain of key comparators that recompute each
 * node's range centre on every comparison. ClimateSortKeys sorts the same list with the keys computed once per node and
 * a comparator that returns the chain's exact results, so the order is the same (see ClimateSortKeys). When it declines
 * (unexpected node, short parameter space, list not random-access) or the runtime switch is off, the original runs.
 * No Minecraft code is carried: the keys are written from the comparator's behaviour.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree", remap = false)
public abstract class ClimateTreeSortMixin {
    @WrapMethod(method = "m_186937_(Ljava/util/List;IIZ)V")
    private static void bons$keyedSort(List<?> nodes, int dims, int first, boolean abs, Operation<Void> original) {
        if (!ClimateSortKeys.enabled || !ClimateSortKeys.sort(nodes, dims, first, abs)) original.call(nodes, dims, first, abs);
    }
}
