package bons.furious.mixin.regionsunexplored_c2;

import bons.furious.patch.regionsunexplored_c2.FuelBurnMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.regions_unexplored.block.compat.FurnaceBurnTimes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * regionsunexplored_fuel_burn_memo (Regions Unexplored 0.5.6+1.20.1, All Rights Reserved; both sides).
 *
 * Wraps Regions Unexplored's FurnaceFuelBurnTimeEvent listener: the first event for an item runs the listener unchanged
 * while its four event.setBurnTime calls are recorded; later events for that item replay the recorded calls (see
 * {@link FuelBurnMemo} for why that is identical). Only our own logic is carried: a whole-method wrapper and a wrapper
 * around the listener's setBurnTime calls, which always runs the original call.
 */
@Mixin(value = FurnaceBurnTimes.class, remap = false)
public abstract class FurnaceBurnTimesMixin {
    @WrapMethod(method = "burnTime")
    private static void bons$replayFuel(FurnaceFuelBurnTimeEvent event, Operation<Void> original) {
        FuelBurnMemo.burnTime(event, original);
    }

    @WrapOperation(method = "burnTime", require = 4, allow = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/furnace/FurnaceFuelBurnTimeEvent;setBurnTime(I)V"))
    private static void bons$recordFuel(FurnaceFuelBurnTimeEvent event, int time, Operation<Void> original) {
        FuelBurnMemo.setBurnTime(event, time, original);
    }
}
