package bons.furious.patch.sliceanddice_c2;

/**
 * Bons and Furious switch sliceanddice_wet_air_gate: implemented on vanilla's HashMapPalette by
 * HashMapPaletteWetAirScanMixin. It holds WetAirGate's incremental scan of that palette: the number of entries already
 * looked at (low 30 bits) and whether any of them was Slice & Dice's wet air (bit 30) or a crop (bit 31). A
 * HashMapPalette only ever appends entries (idFor) except in read(FriendlyByteBuf), which replaces them in place; the
 * mixin resets the scan there, so a kept scan always describes the palette's current entries.
 */
public interface WetAirPaletteScan {
    int bons$wetAirScan();

    void bons$setWetAirScan(int scan);
}
