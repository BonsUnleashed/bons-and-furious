package bons.furious.mixin.powah_coo49;

import bons.furious.patch.powah_coo49.PowahEnergyPush;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import owmii.powah.util.EnergyUtil;

/**
 * powah_zero_energy_push_skip (Powah 6.2.10, server; kind change, off by default; no Powah code carried):
 * EnergyUtil.pushEnergy (the reactor's push out of its faces) answers 0 for an offer of 0 or less without looking the
 * neighbour's energy capability up or handing it receiveEnergy(0, false). See PowahEnergyPush.
 */
@Mixin(value = EnergyUtil.class, remap = false)
public abstract class PowahEnergyPushMixin {
    @Inject(method = "pushEnergy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;J)J", at = @At("HEAD"),
            cancellable = true, require = 1, allow = 1)
    private static void bons$powahZeroEnergyPushSkip(Level level, BlockPos pos, Direction side, long howMuch, CallbackInfoReturnable<Long> cir) {
        if (PowahEnergyPush.idle(howMuch)) cir.setReturnValue(0L);
    }
}
