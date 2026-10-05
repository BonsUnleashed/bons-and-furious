package bons.furious.mixin.climate_columns;

import bons.furious.patch.climate_columns.ClimateParts;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_climate_sample_xz_parts (Minecraft 1.20.1 world generation): the end of RandomState's constructor tells the
 * climate sampler it just built (f_224549_) which noise router it was made from (f_224548_), so ClimateParts can map the
 * router's functions with their two-dimensional cache markers. Nothing else changes.
 */
@Mixin(value = RandomState.class, remap = false)
public abstract class RandomStatePartsMixin {
    @Shadow @Final private Climate.Sampler f_224549_;
    @Shadow @Final private NoiseRouter f_224548_;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$attachRouter(CallbackInfo ci) {
        ClimateParts.attach(this.f_224549_, this.f_224548_);
    }
}
