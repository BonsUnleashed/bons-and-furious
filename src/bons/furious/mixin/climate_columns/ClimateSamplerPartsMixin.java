package bons.furious.mixin.climate_columns;

import bons.furious.patch.climate_columns.ClimateParts;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_climate_sample_xz_parts (Minecraft 1.20.1 world generation).
 *
 * Climate.Sampler.sample evaluates its six density functions with no cache. Each of those six evaluations now goes
 * through ClimateParts, which answers from copies of the same functions whose two-dimensional parts are remembered per
 * column (see ClimateParts for when a sampler qualifies); a sampler that does not qualify evaluates exactly as before.
 * The router of the RandomState that made the sampler and the preparation state live in fields added here.
 */
@Mixin(value = Climate.Sampler.class, remap = false)
public abstract class ClimateSamplerPartsMixin implements ClimateParts.PartsSampler {
    @Unique   // 1.0.34: transient (all three): Gson reads a record's non-transient fields as components and fails without an accessor
    private transient Object bons$router;
    @Unique
    private transient volatile Object bons$parts;
    @Unique
    private transient int bons$canaryLeft;

    @Override
    public Object bons$partsRouter() {
        return this.bons$router;
    }

    @Override
    public void bons$partsRouter(Object router) {
        this.bons$router = router;
    }

    @Override
    public Object bons$parts() {
        return this.bons$parts;
    }

    @Override
    public void bons$parts(Object parts) {
        this.bons$parts = parts;
    }

    @Override
    public int bons$canary() {
        return this.bons$canaryLeft;
    }

    @Override
    public void bons$canary(int left) {
        this.bons$canaryLeft = left;
    }

    /*
     * The six DensityFunction.compute calls of sample(), in the order the method makes them (temperature, humidity,
     * continentalness, erosion, depth, weirdness; the guard fingerprints the method). Each returns the same value, from
     * the cached copy of that function when the sampler qualified. The index comes from the call's position, not from
     * the function object: several of a sampler's functions can be one shared object (the Nether's zero functions).
     * 1.0.34: require = 0, each redirect is exact on its own. One whose call another mod redirects first (Mixin's
     * "conflict. Skipping" warning) or whose call an @Overwrite of sample() no longer makes is left out, so that call
     * runs as the other mod made it; one that still finds its call evaluates the cached copy only when the call
     * evaluates the sampler's own function of that position (ClimateParts.compute), the original otherwise.
     */
    private static final String COMPUTE = "Lnet/minecraft/world/level/levelgen/DensityFunction;m_207386_(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D";

    @Redirect(method = "m_183445_", at = @At(value = "INVOKE", target = COMPUTE, ordinal = 0), require = 0)
    private double bons$temperature(DensityFunction function, DensityFunction.FunctionContext context) {
        return ClimateParts.compute(this, 0, function, context);
    }

    @Redirect(method = "m_183445_", at = @At(value = "INVOKE", target = COMPUTE, ordinal = 1), require = 0)
    private double bons$humidity(DensityFunction function, DensityFunction.FunctionContext context) {
        return ClimateParts.compute(this, 1, function, context);
    }

    @Redirect(method = "m_183445_", at = @At(value = "INVOKE", target = COMPUTE, ordinal = 2), require = 0)
    private double bons$continentalness(DensityFunction function, DensityFunction.FunctionContext context) {
        return ClimateParts.compute(this, 2, function, context);
    }

    @Redirect(method = "m_183445_", at = @At(value = "INVOKE", target = COMPUTE, ordinal = 3), require = 0)
    private double bons$erosion(DensityFunction function, DensityFunction.FunctionContext context) {
        return ClimateParts.compute(this, 3, function, context);
    }

    @Redirect(method = "m_183445_", at = @At(value = "INVOKE", target = COMPUTE, ordinal = 4), require = 0)
    private double bons$depth(DensityFunction function, DensityFunction.FunctionContext context) {
        return ClimateParts.compute(this, 4, function, context);
    }

    @Redirect(method = "m_183445_", at = @At(value = "INVOKE", target = COMPUTE, ordinal = 5), require = 0)
    private double bons$weirdness(DensityFunction function, DensityFunction.FunctionContext context) {
        return ClimateParts.compute(this, 5, function, context);
    }
}
