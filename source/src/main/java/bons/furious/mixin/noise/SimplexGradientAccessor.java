package bons.furious.mixin.noise;

import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SimplexNoise.class, remap = false)
public interface SimplexGradientAccessor {
    @Accessor("GRADIENT")
    static int[][] bons$gradients() { throw new AssertionError(); }
}
