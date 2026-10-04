package bons.furious.mixin.worldgen_aquifer;

import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_aquifer_high_air (Minecraft 1.20.1 world generation, server side; Forge 47.4.16): read access to
 * Aquifer$FluidStatus.fluidLevel (package-private), which the early air return compares with the block's y. Read only.
 */
@Mixin(value = Aquifer.FluidStatus.class, remap = false)
public interface FluidStatusLevelAccessor {
    @Accessor("f_188400_")
    int bons$fluidLevel();
}
