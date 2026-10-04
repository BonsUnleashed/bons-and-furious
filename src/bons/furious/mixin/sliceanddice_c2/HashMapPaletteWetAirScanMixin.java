package bons.furious.mixin.sliceanddice_c2;

import bons.furious.patch.sliceanddice_c2.WetAirPaletteScan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.HashMapPalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * sliceanddice_wet_air_gate (Slice & Dice 3.6.0, both sides), part 3 of 3: vanilla's HashMapPalette keeps WetAirGate's
 * incremental scan of its entries in one int (WetAirPaletteScan; the field fits the object's existing alignment
 * padding, so palettes do not grow). The palette only appends entries (idFor) except in read(FriendlyByteBuf), which
 * clears and refills them in place: the kept scan is reset there, before the refill. The palette itself behaves exactly
 * as before; only WetAirGate reads or writes the field.
 */
@Mixin(value = HashMapPalette.class, remap = false)
public abstract class HashMapPaletteWetAirScanMixin implements WetAirPaletteScan {
    @Unique
    private int bons$wetAirScanState;

    @Override
    public int bons$wetAirScan() {
        return this.bons$wetAirScanState;
    }

    @Override
    public void bons$setWetAirScan(int scan) {
        this.bons$wetAirScanState = scan;
    }

    @Inject(method = "m_5680_", at = @At("HEAD"))
    private void bons$forgetWetAirScan(FriendlyByteBuf buf, CallbackInfo ci) {
        this.bons$wetAirScanState = 0;
    }
}
