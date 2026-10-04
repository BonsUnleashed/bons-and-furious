package bons.furious.patch.beardifier;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;

/**
 * Bons and Furious switch worldgen_empty_beardifier_marker: what BeardifierEmptyFillMixin adds to
 * net.minecraft.world.level.levelgen.Beardifier (Minecraft 1.21.1, NeoForge 21.1.252; server side of world generation).
 *
 * The flag is set at most once per Beardifier, by EmptyBeardifiers.mark, right after NoiseBasedChunkGenerator built it
 * for one chunk and before any NoiseChunk used it. The two iterators are vanilla's own piece and junction iterators.
 *
 * Ported to 1.21.1: unchanged (no Minecraft types).
 */
public interface EmptyFillBeardifier {
    /** Marks this beardifier as one that adds 0.0 at every position (see EmptyBeardifiers). */
    void bons$markEmptyFill();

    /** True when bons$markEmptyFill was called. */
    boolean bons$isEmptyFill();

    /** Vanilla's rigid piece iterator (Beardifier.pieceIterator). */
    ObjectListIterator<?> bons$pieceIterator();

    /** Vanilla's jigsaw junction iterator (Beardifier.junctionIterator). */
    ObjectListIterator<?> bons$junctionIterator();
}
