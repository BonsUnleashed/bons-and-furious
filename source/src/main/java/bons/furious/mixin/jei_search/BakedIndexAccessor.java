package bons.furious.mixin.jei_search;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import mezz.jei.modshade.net.mezzdev.bakedsubstring.BakedSubstringIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * jei_baked_index_background_grams (JEI 19.51.0.418 for Minecraft 1.21.1 / NeoForge, tested build
 * jei-1.21.1-neoforge-19.51.0.418.jar; client): read access to the four final fields of JEI's shaded
 * BakedSubstringIndex, for the shadow-mode comparison only. Nothing in the class is changed.
 *
 * Ported to 1.21.1: no change (keys, values, entriesByGram and deduplicateResults keep their names and types).
 */
@Mixin(value = BakedSubstringIndex.class, remap = false)
public interface BakedIndexAccessor {
    @Accessor(value = "keys", remap = false)
    String[] bons$indexKeys();

    @Accessor(value = "values", remap = false)
    Object[] bons$indexValues();

    @Accessor(value = "entriesByGram", remap = false)
    Long2ObjectOpenHashMap<int[]> bons$entriesByGram();

    @Accessor(value = "deduplicateResults", remap = false)
    boolean bons$deduplicateResults();
}
