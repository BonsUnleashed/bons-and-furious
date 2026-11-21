package bons.furious.mixin.alexscaves_dimension_gate;

import com.github.alexmodguy.alexscaves.server.config.BiomeGenerationNoiseCondition;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * alexscaves_rare_biome_dimension_first: read access to a biome condition's dimension list (the list its test checks last).
 */
@Mixin(value = BiomeGenerationNoiseCondition.class, remap = false)
public interface ConditionDimensionsAccessor {
    @Accessor(value = "dimensions", remap = false)
    List<String> bons$acDimensions();
}
