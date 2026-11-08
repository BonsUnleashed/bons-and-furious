package bons.furious.mixin.hexerei_coo49;

import bons.furious.patch.hexerei_coo49.HexLights;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * hexerei_empty_light_scan_fast_path (Hexerei 0.5.0.3 for 1.21.1, MIT: no code carried; client; exact), part 2 of 2: while
 * the copied size of LightManager's light-source set is 0, getDynamicLightLevel and getDynamicLightLevelWorld answer 0.0
 * (the stock scan's Mth.clamp(0.0, 0, 15) over an empty set) without taking the set's read lock or building its iterator.
 */
@Mixin(targets = "net.joefoxe.hexerei.light.LightManager", remap = false)
public abstract class HexEmptyLightScanMixin {
    @Inject(method = "getDynamicLightLevel(Lnet/minecraft/core/BlockPos;)D", at = @At("HEAD"), cancellable = true)
    private static void bons$hexereiEmptyEntityScan(BlockPos pos, CallbackInfoReturnable<Double> cir) {
        if (HexLights.noSources()) cir.setReturnValue(0.0);
    }

    @Inject(method = "getDynamicLightLevelWorld(Lnet/minecraft/core/BlockPos;)D", at = @At("HEAD"), cancellable = true)
    private static void bons$hexereiEmptyWorldScan(BlockPos pos, CallbackInfoReturnable<Double> cir) {
        if (HexLights.noSources()) cir.setReturnValue(0.0);
    }
}
