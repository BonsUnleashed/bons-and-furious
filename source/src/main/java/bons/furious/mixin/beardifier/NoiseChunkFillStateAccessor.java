package bons.furious.mixin.beardifier;

import bons.furious.patch.beardifier.NoiseChunkFillState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * worldgen_empty_beardifier_marker (Minecraft 1.21.1, tested with NeoForge 21.1.252): accessors only.
 * EmptyBeardifiers.fill reads the cell size and leaves the in-cell position and array index where
 * NoiseChunk.fillAllDirectly's loop leaves them. Accessors change no behaviour of NoiseChunk.
 *
 * Ported to 1.21.1: same six NoiseChunk fields (cellWidth, cellHeight final; inCellX/Y/Z, arrayIndex), Mojang names.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public interface NoiseChunkFillStateAccessor extends NoiseChunkFillState {
    @Override
    @Accessor("cellWidth")
    int bons$cellWidth();

    @Override
    @Accessor("cellHeight")
    int bons$cellHeight();

    @Override
    @Accessor("inCellX")
    void bons$setInCellX(int value);

    @Override
    @Accessor("inCellY")
    void bons$setInCellY(int value);

    @Override
    @Accessor("inCellZ")
    void bons$setInCellZ(int value);

    @Override
    @Accessor("arrayIndex")
    void bons$setArrayIndex(int value);

    @Override
    @Accessor("inCellX")
    int bons$inCellX();

    @Override
    @Accessor("inCellY")
    int bons$inCellY();

    @Override
    @Accessor("inCellZ")
    int bons$inCellZ();

    @Override
    @Accessor("arrayIndex")
    int bons$arrayIndex();
}
