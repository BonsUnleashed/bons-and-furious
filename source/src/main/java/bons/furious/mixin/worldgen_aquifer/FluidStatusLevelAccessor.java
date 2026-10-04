package bons.furious.mixin.worldgen_aquifer;

import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_aquifer_high_air (Minecraft 1.21.1 world generation, server side; tested build NeoForge 21.1.252): read access
 * to Aquifer$FluidStatus.fluidLevel (package-private), which the early air return compares with the block's y. Read only.
 *
 * Ported to 1.21.1: unchanged (FluidStatus is still a final class with package-private final int fluidLevel).
 */
@Mixin(value = Aquifer.FluidStatus.class, remap = false)
public interface FluidStatusLevelAccessor {
    @Accessor("fluidLevel")
    int bons$fluidLevel();
}
