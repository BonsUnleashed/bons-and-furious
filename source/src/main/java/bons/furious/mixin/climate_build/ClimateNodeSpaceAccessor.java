package bons.furious.mixin.climate_build;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_climate_tree_sort_keys (Minecraft 1.21.1, both sides; tested with NeoForge 21.1.252): read access to
 * Climate.RTree.Node's parameter space (the final array the tree's comparators read). Read only.
 *
 * Ported to 1.21.1: unchanged; Node keeps {@code protected final Climate.Parameter[] parameterSpace}.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree$Node", remap = false)
public interface ClimateNodeSpaceAccessor {
    @Accessor(value = "parameterSpace", remap = false)
    Climate.Parameter[] bons$parameterSpace();
}
