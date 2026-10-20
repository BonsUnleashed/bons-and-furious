package bons.furious.mixin.vanilla_climate_distance;

import bons.furious.patch.vanilla_climate_distance.ClimateDistanceReuse;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * vanilla_climate_search_distance_reuse (Minecraft 1.20.1, both sides; since 1.0.46): the vanilla metric's registration.
 *
 * Climate.ParameterList.findValueIndex (m_186851_) passes the method reference Climate.RTree.Node::distance (its guarded
 * invokedynamic; non-capturing, so one object) to findValue (m_186853_), which searches the tree. This wrapper registers
 * that object with ClimateDistanceReuse (the tree walk then may reuse its distances) and makes the same call with the same
 * arguments; the result is returned unchanged. The metric type is not public, hence the coerced parameter.
 */
@Mixin(value = Climate.ParameterList.class, remap = false)
public abstract class ClimateDistanceVanillaSiteMixin {
    @WrapOperation(method = "m_186851_", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/biome/Climate$ParameterList;m_186853_(Lnet/minecraft/world/level/biome/Climate$TargetPoint;Lnet/minecraft/world/level/biome/Climate$DistanceMetric;)Ljava/lang/Object;"))
    private Object bons$knownVanillaMetric(Climate.ParameterList<?> self, Climate.TargetPoint target, @Coerce Object metric, Operation<Object> original) {
        ClimateDistanceReuse.know(metric);
        return original.call(self, target, metric);
    }
}
