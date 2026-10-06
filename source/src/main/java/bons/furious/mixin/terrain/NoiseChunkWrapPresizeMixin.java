package bons.furious.mixin.terrain;

import bons.furious.patch.terrain.WrapPresize;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_noise_wrap_presize (Minecraft 1.20.1 world generation, both sides), part 1 of 2.
 *
 * Just before the constructor wraps the noise router (its first use of the wrap table), the still-empty table is
 * replaced by one of the same class sized for the entry count this RandomState's previous NoiseChunk reached; at the
 * end of the constructor the reached size is recorded. See {@link WrapPresize} for why the result is identical. The
 * table (wrapped) is read only by wrap (wrap; ModernFix overwrites it as get/put) and by
 * terrain_final_density_reuse. BetterEnd, Bumblezone and YUNG's Cave Biomes hook other points of this class.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkWrapPresizeMixin {
    @Shadow
    @Final
    @Mutable
    private Map<DensityFunction, DensityFunction> wrapped;

    // Mixin 0.8.5 accepts no @Inject in the middle of a constructor; @ModifyArg on the router's mapAll call runs at the
    // same point and hands the visitor through unchanged.
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"),
            require = 0)   // never refused; bons.furious.guard.CallSites decides whether the table is presized
    private DensityFunction.Visitor bons$presizeWrapTable(DensityFunction.Visitor visitor, @Local(argsOnly = true) RandomState random) {
        this.wrapped = WrapPresize.presized(this.wrapped, random);
        return visitor;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$learnWrapTableSize(CallbackInfo ci, @Local(argsOnly = true) RandomState random) {
        WrapPresize.learn(this.wrapped, random);
    }
}
