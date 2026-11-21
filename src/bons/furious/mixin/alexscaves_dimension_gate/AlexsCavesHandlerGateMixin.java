package bons.furious.mixin.alexscaves_dimension_gate;

import bons.furious.patch.alexscaves_dimension_gate.DimensionGate;
import com.bawnorton.mixinsquared.TargetHandler;
import com.github.alexmodguy.alexscaves.server.config.BiomeGenerationConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * alexscaves_rare_biome_dimension_first (Alex's Caves 2.0.2, LGPL-3.0; both sides): MixinSquared @TargetHandler injections
 * into Alex's Caves' MultiNoiseBiomeSourceMixin.citadel_getNoiseBiomeCoords, merged into MultiNoiseBiomeSource.getNoiseBiome
 * (m_203407_). HEAD (cancellable): when no Alex's Caves biome condition lists the dimension the source was last given, the
 * handler is ended before its own work (see DimensionGate for why that is the handler's own outcome). RETURN (verify mode
 * only): checks that the handler never set a biome in a lookup the gate would skip. The dimension is Alex's Caves'
 * lastSampledDimension, the plain private field its mixin adds to MultiNoiseBiomeSource and its condition test reads
 * (DimensionGate.lastDimension: a getter handle, since Mixin cannot shadow a field another mixin of the same pass adds).
 * Priority 1500: applied after Alex's Caves' mixin (-69420). No Alex's Caves code is carried: our own decision logic only.
 */
@Mixin(value = MultiNoiseBiomeSource.class, priority = 1500, remap = false)
public abstract class AlexsCavesHandlerGateMixin {
    @Unique
    private volatile DimensionGate.Decision bons$acGate;

    @Unique
    private DimensionGate.Decision bons$acGateDecision() {
        DimensionGate.Decision d = DimensionGate.decide(this.bons$acGate, DimensionGate.lastDimension(this), BiomeGenerationConfig.BIOMES.values(),
                c -> ((ConditionDimensionsAccessor) c).bons$acDimensions());
        this.bons$acGate = d;
        return d;
    }

    @TargetHandler(mixin = "com.github.alexmodguy.alexscaves.mixin.MultiNoiseBiomeSourceMixin", name = "citadel_getNoiseBiomeCoords")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$acGateHead(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> theirs, CallbackInfo ci) {
        if (!DimensionGate.ON) return;
        boolean skip = bons$acGateDecision().skip();
        DimensionGate.count(skip);
        if (skip && DimensionGate.ACTIVE) ci.cancel();
    }

    @TargetHandler(mixin = "com.github.alexmodguy.alexscaves.mixin.MultiNoiseBiomeSourceMixin", name = "citadel_getNoiseBiomeCoords")
    @Inject(method = "@MixinSquared:Handler", at = @At("RETURN"), require = 0, expect = 0)
    private void bons$acGateVerify(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> theirs, CallbackInfo ci) {
        if (!DimensionGate.VERIFY) return;
        DimensionGate.Decision d = this.bons$acGate;
        if (d != null && d.skip() && theirs.isCancelled()) DimensionGate.wrong(DimensionGate.lastDimension(this), x, y, z);
    }
}
