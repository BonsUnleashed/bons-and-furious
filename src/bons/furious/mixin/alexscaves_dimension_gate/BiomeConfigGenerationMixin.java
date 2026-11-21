package bons.furious.mixin.alexscaves_dimension_gate;

import bons.furious.patch.alexscaves_dimension_gate.DimensionGate;
import com.github.alexmodguy.alexscaves.server.config.BiomeGenerationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * alexscaves_rare_biome_dimension_first: a new decision generation each time Alex's Caves rebuilds its biome conditions
 * from the config files (BiomeGenerationConfig.reloadConfig), so a remembered decision never outlives the lists it read.
 */
@Mixin(value = BiomeGenerationConfig.class, remap = false)
public abstract class BiomeConfigGenerationMixin {
    @Inject(method = "reloadConfig", at = @At("RETURN"))
    private static void bons$acGateNewGeneration(CallbackInfo ci) {
        DimensionGate.generation++;
    }
}
