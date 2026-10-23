package bons.furious.mixin.vanilla_climate_distance;

import bons.furious.patch.vanilla_climate_distance.ClimateDistanceReuse;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * terrablender_climate_search_distance_reuse (TerraBlender 3.0.1.10 with Minecraft 1.20.1, both sides; since 1.0.46):
 * TerraBlender's metric registration for vanilla_climate_search_distance_reuse.
 *
 * TerraBlender's MixinParameterList merges findValuePositional into Climate.ParameterList; it searches a region's tree with
 * RTree.search (search), passing the method reference Climate.RTree.Node::distance (two call sites, each one
 * non-capturing object; the switch guards TerraBlender's bytecode for exactly that). This wrapper registers the object
 * with ClimateDistanceReuse and makes the same call with the same arguments, returning its result unchanged. Its priority
 * is above TerraBlender's (1000) so the method TerraBlender merges is there when this mixin applies. The tree and metric
 * types are not public, hence the coerced parameters.
 */
@Mixin(value = Climate.ParameterList.class, priority = 1100, remap = false)
public abstract class ClimateDistanceTerraBlenderSiteMixin {
    @WrapOperation(method = "findValuePositional", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/biome/Climate$RTree;search(Lnet/minecraft/world/level/biome/Climate$TargetPoint;Lnet/minecraft/world/level/biome/Climate$DistanceMetric;)Ljava/lang/Object;"))
    private Object bons$knownTerraBlenderMetric(@Coerce Object tree, Climate.TargetPoint target, @Coerce Object metric, Operation<Object> original) {
        ClimateDistanceReuse.know(metric);
        return original.call(tree, target, metric);
    }
}
