package bons.furious.patch.beardifier;

/**
 * Bons and Furious switch worldgen_empty_beardifier_marker: accessors NoiseChunkFillStateAccessor adds to
 * net.minecraft.world.level.levelgen.NoiseChunk (Minecraft 1.21.1, NeoForge 21.1.252). They read the cell size and
 * write the in-cell position and array index that NoiseChunk.fillAllDirectly leaves behind, so a fill answered without
 * that loop leaves the NoiseChunk in the same state as the loop would.
 *
 * Ported to 1.21.1: unchanged; the six fields keep their names and types.
 */
public interface NoiseChunkFillState {
    /** NoiseChunk.cellWidth. */
    int bons$cellWidth();

    /** NoiseChunk.cellHeight. */
    int bons$cellHeight();

    /** NoiseChunk.inCellX. */
    void bons$setInCellX(int value);

    /** NoiseChunk.inCellY. */
    void bons$setInCellY(int value);

    /** NoiseChunk.inCellZ. */
    void bons$setInCellZ(int value);

    /** NoiseChunk.arrayIndex. */
    void bons$setArrayIndex(int value);

    /** NoiseChunk.inCellX, for the shadow check and the offline proof. */
    int bons$inCellX();

    /** NoiseChunk.inCellY, for the shadow check and the offline proof. */
    int bons$inCellY();

    /** NoiseChunk.inCellZ, for the shadow check and the offline proof. */
    int bons$inCellZ();

    /** NoiseChunk.arrayIndex, for the shadow check and the offline proof. */
    int bons$arrayIndex();
}
