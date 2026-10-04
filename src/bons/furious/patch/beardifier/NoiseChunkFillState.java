package bons.furious.patch.beardifier;

/**
 * Bons and Furious switch worldgen_empty_beardifier_marker: accessors NoiseChunkFillStateAccessor adds to
 * net.minecraft.world.level.levelgen.NoiseChunk (Minecraft 1.20.1). They read the cell size and write the in-cell
 * position and array index that NoiseChunk.fillAllDirectly leaves behind, so a fill answered without that loop leaves
 * the NoiseChunk in the same state as the loop would.
 */
public interface NoiseChunkFillState {
    /** NoiseChunk.cellWidth (f_209170_). */
    int bons$cellWidth();

    /** NoiseChunk.cellHeight (f_209171_). */
    int bons$cellHeight();

    /** NoiseChunk.inCellX (f_209153_). */
    void bons$setInCellX(int value);

    /** NoiseChunk.inCellY (f_209154_). */
    void bons$setInCellY(int value);

    /** NoiseChunk.inCellZ (f_209155_). */
    void bons$setInCellZ(int value);

    /** NoiseChunk.arrayIndex (f_209158_). */
    void bons$setArrayIndex(int value);

    /** NoiseChunk.inCellX (f_209153_), for the shadow check and the offline proof. */
    int bons$inCellX();

    /** NoiseChunk.inCellY (f_209154_), for the shadow check and the offline proof. */
    int bons$inCellY();

    /** NoiseChunk.inCellZ (f_209155_), for the shadow check and the offline proof. */
    int bons$inCellZ();

    /** NoiseChunk.arrayIndex (f_209158_), for the shadow check and the offline proof. */
    int bons$arrayIndex();
}
