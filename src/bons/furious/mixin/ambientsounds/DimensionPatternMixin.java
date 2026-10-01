package bons.furious.mixin.ambientsounds;

import bons.furious.patch.ambientsounds.DimensionPatterns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ambientsounds_dimension_patterns (AmbientSounds 6.3.8, client): both String.matches calls of AmbientDimension.is (the
 * excluded and the included dimension names) go through DimensionPatterns, which keeps each compiled pattern. Separate
 * from 1.0.21's ambientsounds_biome_match_cache, which covers biome conditions.
 */
@Mixin(targets = "team.creative.ambientsounds.dimension.AmbientDimension", remap = false)
public abstract class DimensionPatternMixin {
    @Redirect(method = "is", at = @At(value = "INVOKE", target = "Ljava/lang/String;matches(Ljava/lang/String;)Z"))
    private boolean bons$compiledMatch(String input, String regex) {
        return DimensionPatterns.matches(input, regex);
    }
}
