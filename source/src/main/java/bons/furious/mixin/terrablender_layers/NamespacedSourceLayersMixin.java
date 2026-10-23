package bons.furious.mixin.terrablender_layers;

import bons.furious.patch.terrablender_layers.StackedLayers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * terrablender_stacked_layer_dedupe (TerraBlender 3.0.1.10 and 3.0.1.11, whose NamespacedSurfaceRuleSource is byte-identical;
 * LGPL-3.0; server side of world generation). Exact; nothing about the saved data changes.
 *
 * NamespacedSurfaceRuleSource.apply(context) ends with new NamespacedRule(context, base.apply(context), rules). The base.apply
 * call is wrapped: when base is itself a stack of NamespacedSurfaceRuleSource layers (a world whose level.dat kept
 * TerraBlender's wrapper and got wrapped again at every open), the call applies StackedLayers' flattened chain instead (inner
 * namespace entries equal to an outer layer's skipped, empty layers removed, memoised per top-level source). Any other base
 * is applied unchanged. StackedLayers explains why every block is the same. Runs once per chunk surface pass and layer.
 */
@Mixin(targets = "terrablender.worldgen.surface.NamespacedSurfaceRuleSource", remap = false)
public abstract class NamespacedSourceLayersMixin {
    @WrapOperation(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;apply(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object bons$flattenStackedBase(SurfaceRules.RuleSource base, Object context, Operation<Object> original) {
        SurfaceRules.RuleSource use = StackedLayers.baseFor(this, base);
        if (use == base) return original.call(base, context);
        Object flattened = original.call(use, context);
        return StackedLayers.SHADOW ? StackedLayers.shadow(flattened, original.call(base, context)) : flattened;
    }
}
