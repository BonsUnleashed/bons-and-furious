package bons.furious.patch.worldgen_biome_fiddle;

/**
 * Bons and Furious switch vanilla_biome_fiddle_mask (Minecraft 1.20.1, both sides: every biome lookup of the world, the
 * client's included). SRG member names. Idea: Tpsum's BiomeLookup notes ("floorMod(seed >> 24, 1024) with
 * (seed >> 24) & 1023"), idea text only.
 *
 * What it costs. BiomeManager.getBiome blurs biome borders: for each of 8 corner cells it derives three "fiddle" offsets,
 * each `Math.floorMod(seed >> 24, 1024)`. floorMod's sign fix-up is a data-dependent branch on a pseudo-random seed, 24
 * times per lookup.
 *
 * What the switch does. BiomeFiddleMaskMixin redirects that one floorMod call inside BiomeManager.getFiddle to
 * `(int) (x & 1023)` when the divisor is 1024 (it always is there). For a power-of-two divisor floorMod is exactly the low
 * bits of the two's complement value (0..1023 for every long, negative ones included), so every offset, distance and
 * chosen biome is identical. Any other divisor runs Math.floorMod.
 *
 * -Dbons_and_furious.vanillaBiomeFiddleMask=false: Math.floorMod (checked per call).
 */
public final class BiomeFiddleMask {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaBiomeFiddleMask", "true"));

    private BiomeFiddleMask() {
    }

    /** floorMod(x, y), computed as a mask for y = 1024 (identical for every long x). */
    public static int floorMod(long x, int y) {
        return y == 1024 && enabled ? (int) (x & 1023L) : Math.floorMod(x, y);
    }
}
