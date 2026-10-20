package bons.furious.mixin.subtleeffects;

import bons.furious.patch.subtleeffects.IdleFluids;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * subtleeffects_idle_fluid_hooks, lava part (Subtle Effects 1.15.1-hotfix.1, client): MixinSquared @TargetHandler
 * injections into Subtle Effects' LavaFluidMixin.animateTick handler (merged into LavaFluid.animateTick). The first sits
 * just before the handler's 3 x 3 walk (its first int constant 1, the start of the not-falling branch) and returns at once
 * when the walk would return at its centre cell anyway (IdleFluids.skipLava explains why nothing observable changes). The
 * second passes the handler's first random draw through unchanged and serves only the shadow mode's check. Our own logic
 * only. Priority 1500: after Subtle Effects' mixin. require = 0: without that handler there is nothing to change.
 */
@Mixin(value = LavaFluid.class, priority = 1500, remap = false)
public abstract class LavaSparksIdleMixin {
    @TargetHandler(mixin = "einstein.subtle_effects.mixin.client.fluid.LavaFluidMixin", name = "animateTick")
    @Inject(method = "@MixinSquared:Handler", at = @At(value = "CONSTANT", args = "intValue=1", ordinal = 0), cancellable = true, require = 0, expect = 0)
    private void bons$seLavaCovered(Level level, BlockPos pos, FluidState state, RandomSource random, CallbackInfo theirs, CallbackInfo ci) {
        if (IdleFluids.skipLava((Fluid) (Object) this, level, pos)) ci.cancel();
    }

    @TargetHandler(mixin = "einstein.subtle_effects.mixin.client.fluid.LavaFluidMixin", name = "animateTick")
    @ModifyExpressionValue(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;m_188500_()D", ordinal = 0), require = 0, expect = 0)
    private double bons$seLavaShadowDraw(double value) {
        return IdleFluids.lavaDraw(value);
    }
}
