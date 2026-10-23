package bons.furious.mixin.noise;

import bons.furious.patch.noise.GradientTable;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ImprovedNoise.class, remap = false, priority = 1500)
public abstract class ImprovedNoiseGradientMixin {
    @Shadow @Final private byte[] p;
    // Do not allocate tables for thousands of noises in dimensions never sampled.
    // A racing first sample may construct an identical extra table; volatile
    // publication guarantees visibility and never exposes a partly filled array.
    @Unique private volatile double[] bons$gradientTable;

    @Redirect(method = "noise(DDDDD)D", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/ImprovedNoise;sampleAndLerp(IIIDDDD)D"), require = 0)
    private double bons$gradientSample(ImprovedNoise noise, int ix, int iy, int iz, double x, double y, double z, double fadeY) {
        if (!bons.furious.patch.noise.NoiseCompatibility.allowed || noise != (Object) this)
            return ((ImprovedNoiseInvoker) (Object) noise).bons$originalSample(ix, iy, iz, x, y, z, fadeY);
        double[] table = bons$gradientTable;
        if (table == null) {
            table = GradientTable.build(p, SimplexGradientAccessor.bons$gradients());
            bons$gradientTable = table;
        }
        return GradientTable.sample(p, table, ix, iy, iz, x, y, z, fadeY);
    }
}
