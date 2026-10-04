package bons.furious.mixin.climate_build;

import bons.furious.patch.climate_build.ClimateSpanBounds;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_climate_tree_span_bounds (Minecraft 1.20.1 world generation setup, both sides).
 *
 * Climate.RTree.buildParameterSpace(children) widens seven running ranges with Parameter.span, a new Parameter per child
 * and dimension. ClimateSpanBounds computes the same seven ranges directly (same values, the same object identity
 * rules, a new ArrayList); when it declines or the runtime switch is off, the original runs. No Minecraft code is
 * carried: the ranges are written from the method's behaviour.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree", remap = false)
public abstract class ClimateTreeSpanMixin {
    @WrapMethod(method = "m_186946_(Ljava/util/List;)Ljava/util/List;")
    private static List<Climate.Parameter> bons$directSpan(List<?> children, Operation<List<Climate.Parameter>> original) {
        if (!ClimateSpanBounds.enabled) return original.call(children);
        List<Climate.Parameter> space = ClimateSpanBounds.span(children);
        return space != null ? space : original.call(children);
    }
}
