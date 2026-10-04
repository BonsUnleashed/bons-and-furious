package bons.furious.patch.radium_fixes;

/**
 * Bons and Furious switch radium_hash_palette_copy (Radium Re-Reforged 0.14.3, both sides). No Radium code here.
 *
 * Fix (latent in this pack). Radium's mixin.chunk.palette (on by default, applied here) stores block-state and biome
 * palettes of 3..8 bits in its LithiumHashPalette: a Reference2IntOpenHashMap from entry to id whose default return
 * value is -1, so index(entry) knows an entry is new and appends it (or asks the container to resize). copy() builds the
 * copy's map with the fastutil copy constructor, which copies the entries but not the default return value: a copy
 * answers 0 for every entry it does not hold. Writing a new block state or biome into a copied container then stores
 * palette entry 0 (silent corruption of the copy) instead of the new value, and never resizes. Vanilla's
 * HashMapPalette.copy() appends the new value with the next id. With the switch the copy's map gets the original's default
 * return value, so the copy indexes exactly as the original and as vanilla's palette do. Nothing else of copy() changes.
 *
 * Reach: PalettedContainer.copy() is called in this pack only for client render snapshots (vanilla RenderChunk,
 * Embeddium's section clones), which never write into the copy, so the corruption cannot happen here; it can in packs
 * whose mods write into copied sections.
 */
public final class HashPaletteCopy {
    /** Runtime switch. -Dbons_and_furious.radiumHashPaletteCopy=false turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.radiumHashPaletteCopy", "true"));

    private HashPaletteCopy() {
    }
}
