package bons.furious.mixin.immersiveweathering_c49;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * immersiveweathering_fluid_generator_lean (Immersive Weathering 1.20.1-2.0.5, LGPLv3; server side): read access to the
 * four final rule fields of SelfFluidGenerator$AdjacentBlocks for FluidLean's twin of isMet. Nothing is written.
 */
@Mixin(targets = "com.ordana.immersive_weathering.data.fluid_generators.SelfFluidGenerator$AdjacentBlocks", remap = false)
public interface AdjacentBlocksAccessor {
    @Accessor(value = "anyBlocks", remap = false)
    List<RuleTest> bons$iwC49AnyBlocks();

    @Accessor(value = "sidesBlocks", remap = false)
    List<RuleTest> bons$iwC49SidesBlocks();

    @Accessor(value = "upBlock", remap = false)
    RuleTest bons$iwC49UpBlock();

    @Accessor(value = "downBlock", remap = false)
    RuleTest bons$iwC49DownBlock();
}
