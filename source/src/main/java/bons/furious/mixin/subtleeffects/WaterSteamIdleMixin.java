package bons.furious.mixin.subtleeffects;

import bons.furious.patch.subtleeffects.IdleFluids;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * subtleeffects_idle_fluid_hooks (Subtle Effects 1.15.1-hotfix.1, client): a MixinSquared @TargetHandler HEAD injection
 * into Subtle Effects' WaterFluidMixin.animateTick handler (merged into WaterFluid.animateTick) that returns at once while
 * both steam settings are off (IdleFluids explains why nothing observable changes). Our own logic only. Priority 1500:
 * after Subtle Effects' mixin. require = 0: without that handler there is nothing to change.
 */
@Mixin(value = WaterFluid.class, priority = 1500, remap = false)
public abstract class WaterSteamIdleMixin {
    @TargetHandler(mixin = "einstein.subtle_effects.mixin.client.fluid.WaterFluidMixin", name = "animateTick")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$seWaterSteamIdle(Level level, BlockPos pos, FluidState state, RandomSource random, CallbackInfo theirs, CallbackInfo ci) {
        if (IdleFluids.skipWater()) ci.cancel();
    }
}
