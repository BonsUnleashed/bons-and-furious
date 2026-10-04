package bons.furious.mixin.beardifier;

import bons.furious.patch.beardifier.NoiseChunkFillState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * worldgen_empty_beardifier_marker (Minecraft 1.20.1, Forge 47.4.16): accessors only. EmptyBeardifiers.fill reads the
 * cell size and leaves the in-cell position and array index where NoiseChunk.fillAllDirectly's loop leaves them. Accessors
 * change no behaviour of NoiseChunk.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public interface NoiseChunkFillStateAccessor extends NoiseChunkFillState {
    @Override
    @Accessor("f_209170_")
    int bons$cellWidth();

    @Override
    @Accessor("f_209171_")
    int bons$cellHeight();

    @Override
    @Accessor("f_209153_")
    void bons$setInCellX(int value);

    @Override
    @Accessor("f_209154_")
    void bons$setInCellY(int value);

    @Override
    @Accessor("f_209155_")
    void bons$setInCellZ(int value);

    @Override
    @Accessor("f_209158_")
    void bons$setArrayIndex(int value);

    @Override
    @Accessor("f_209153_")
    int bons$inCellX();

    @Override
    @Accessor("f_209154_")
    int bons$inCellY();

    @Override
    @Accessor("f_209155_")
    int bons$inCellZ();

    @Override
    @Accessor("f_209158_")
    int bons$arrayIndex();
}
