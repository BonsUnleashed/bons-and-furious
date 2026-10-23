package bons.furious.mixin.terrablender_layers;

import bons.furious.patch.terrablender_layers.StackedLayers;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * terrablender_stacked_layer_save (Fix; TerraBlender 3.0.1.10 and 3.0.1.11, LGPL-3.0; server side of world generation).
 *
 * SurfaceRuleManager.getNamespacedRules(category, fallback) returns NamespacedSurfaceRuleSource(fallback, namespaces);
 * TerraBlender's MixinNoiseGeneratorSettings caches that value and returns it as the settings' surface rule, which the
 * settings codec also saves. When fallback is a decoded stack of earlier wrappers, the returned value is the same top layer
 * over StackedLayers' flattened chain instead, so the world generates from it and level.dat stops growing by a full copy of
 * every namespace per world open; an already stacked level.dat collapses at its next save. Generated blocks are identical
 * (StackedLayers). Any other fallback: the value is returned unchanged.
 */
@Mixin(targets = "terrablender.api.SurfaceRuleManager", remap = false)
public abstract class SurfaceRuleManagerLayersMixin {
    @ModifyReturnValue(method = "getNamespacedRules(Lterrablender/api/SurfaceRuleManager$RuleCategory;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;",
            at = @At("RETURN"))
    private static SurfaceRules.RuleSource bons$flattenSavedStack(SurfaceRules.RuleSource built) {
        return StackedLayers.forSave(built);
    }
}
