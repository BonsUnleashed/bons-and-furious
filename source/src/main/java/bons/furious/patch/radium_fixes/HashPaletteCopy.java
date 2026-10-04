package bons.furious.patch.radium_fixes;

/**
 * Bons and Furious switch radium_hash_palette_copy (Radium, LGPL-3.0; 1.21.1 tested build
 * radium-mc1.21.1-0.13.1+git.4994e83; both sides). No Radium code here.
 *
 * Fix (latent). Radium's mixin.chunk.palette (on by default) stores block-state and biome palettes of 3..8 bits in its
 * LithiumHashPalette: a Reference2IntOpenHashMap from entry to id whose default return value is -1, so idFor(entry) knows
 * an entry is new and appends it (or asks the container to resize). copy() builds the copy's map with the fastutil copy
 * constructor, which copies the entries but not the default return value: a copy answers 0 for every entry it does not
 * hold. Writing a new block state or biome into a copied container then stores palette entry 0 (silent corruption of the
 * copy) instead of the new value, and never resizes. Vanilla's HashMapPalette.copy() appends the new value with the next
 * id. With the switch the copy's map gets the original's default return value, so the copy indexes exactly as the
 * original and as vanilla's palette do. Nothing else of copy() changes.
 *
 * Reach: PalettedContainer.copy() is used for client render snapshots, which never write into the copy; it matters for
 * mods that write into copied sections.
 *
 * Ported to 1.21.1: Radium 0.13.1's LithiumHashPalette.copy() still uses new Reference2IntOpenHashMap(this.table) and
 * idFor still tests the map's answer against -1 (ABSENT_VALUE); the only other change in the class (valueFor throws
 * MissingPaletteEntryException instead of returning null) does not touch the copy's map.
 */
public final class HashPaletteCopy {
    /** Runtime switch. -Dbons_and_furious.radiumHashPaletteCopy=false turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.radiumHashPaletteCopy", "true"));

    private HashPaletteCopy() {
    }
}
