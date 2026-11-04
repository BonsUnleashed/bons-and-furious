package bons.furious.mixin.immersiveweathering_c49;

import bons.furious.patch.immersiveweathering_c49.FluidLean;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.ordana.immersive_weathering.data.fluid_generators.FluidGeneratorsHandler;
import com.ordana.immersive_weathering.data.fluid_generators.IFluidGenerator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * immersiveweathering_fluid_generator_lean (Immersive Weathering 1.20.1-2.0.5, LGPLv3; server side): a cancellable HEAD
 * inject on FluidGeneratorsHandler.generate answers from FluidLean (the same generators, reads, rule calls, random draws,
 * placements and result without the per-check allocations); FluidLean answers null, before doing anything, for a list it
 * does not cover (then Immersive Weathering's own body runs).
 */
@Mixin(value = FluidGeneratorsHandler.class, remap = false)
public abstract class FluidGeneratorsLeanMixin {
    @Inject(method = "generate(Ljava/util/List;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lcom/google/common/collect/ImmutableList;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$iwC49Generate(List<Direction> dirs, BlockPos pos, Level level, ImmutableList<IFluidGenerator> list,
                                           CallbackInfoReturnable<Optional<Pair<BlockPos, SoundEvent>>> cir) {
        if (!FluidLean.enabled) return;
        Optional<Pair<BlockPos, SoundEvent>> r = FluidLean.generate(dirs, pos, level, list);
        if (r != null) cir.setReturnValue(r);
    }
}
