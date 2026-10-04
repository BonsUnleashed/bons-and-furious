package bons.furious.mixin.climate_build;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_climate_tree_span_bounds (Minecraft 1.20.1, both sides): read access to Climate.RTree.Node's parameter space,
 * the array buildParameterSpace reads (its own accessor, so the switch does not depend on vanilla_climate_tree_sort_keys).
 * Read only.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree$Node", remap = false)
public interface ClimateNodeSpanAccessor {
    @Accessor(value = "f_186956_", remap = false)
    Climate.Parameter[] bons$spanSpace();
}
