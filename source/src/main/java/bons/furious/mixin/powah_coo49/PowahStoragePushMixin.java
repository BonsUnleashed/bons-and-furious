package bons.furious.mixin.powah_coo49;

import bons.furious.patch.powah_coo49.PowahEnergyPush;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import owmii.powah.lib.block.AbstractEnergyStorage;

/**
 * powah_zero_energy_push_skip (Powah 6.2.10, server; kind change, off by default; no Powah code carried): the
 * receiveEnergy(saturatedCast(min(transfer, stored)), false) offer AbstractEnergyStorage.extractFromSides hands the cached
 * neighbour capability of each extracting face (cells, panels, generators, ender blocks, dischargers) answers 0
 * for an offer of 0 or less without calling the neighbour; the face then extracts 0 as before. See PowahEnergyPush.
 */
@Mixin(value = AbstractEnergyStorage.class, remap = false)
public abstract class PowahStoragePushMixin {
    @WrapOperation(method = "extractFromSides(Lnet/minecraft/world/level/Level;)J", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/energy/IEnergyStorage;receiveEnergy(IZ)I"), require = 1, allow = 1)
    private int bons$powahZeroEnergyFacePush(IEnergyStorage neighbour, int amount, boolean simulate, Operation<Integer> original) {
        return PowahEnergyPush.idle(amount) ? 0 : original.call(neighbour, amount, simulate);
    }
}
