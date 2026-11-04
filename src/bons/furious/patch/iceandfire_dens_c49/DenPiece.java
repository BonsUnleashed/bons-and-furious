package bons.furious.patch.iceandfire_dens_c49;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * iceandfire_structure_dens (since 1.0.49): a dragon cave's one piece. It keeps the cave's kind, seed and centre; its box
 * covers everything Ice and Fire's generator can write for that cave, so every chunk the cave reaches is asked to write its
 * part (DenResult).
 */
public final class DenPiece extends StructurePiece {
    public static final StructurePieceType.ContextlessType TYPE = DenPiece::new;

    final DenKind kind;
    final long seed;
    final BlockPos origin;

    DenPiece(DenKind kind, long seed, BlockPos origin, BoundingBox box) {
        super(TYPE, 0, box);
        this.kind = kind;
        this.seed = seed;
        this.origin = origin;
    }

    private DenPiece(CompoundTag tag) {
        super(TYPE, tag);
        DenKind k = DenKind.byId(tag.m_128461_("Kind"));
        if (k == null) throw new IllegalArgumentException("unknown Ice and Fire den kind " + tag.m_128461_("Kind"));
        this.kind = k;
        this.seed = tag.m_128454_("Seed");
        this.origin = BlockPos.m_122022_(tag.m_128454_("Origin"));
    }

    @Override
    protected void m_183620_(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.m_128359_("Kind", kind.id);
        tag.m_128356_("Seed", seed);
        tag.m_128356_("Origin", origin.m_121878_());
    }

    @Override
    public void m_213694_(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                          ChunkPos chunkPos, BlockPos pivot) {
        DenResult den;
        try {
            den = DenResult.get(this, level, generator);
        } catch (Throwable t) {
            Dens.failOnce("generate a den at " + origin, t);
            return;
        }
        den.commit(level, chunkBox);
    }

    /**
     * A cave's box: the main sphere (radius, Ice and Fire's y cut-off radius / 2) and the side spheres (radius r = 2 *
     * (radius / 3) + 0..7, centred radius - 2 to the north, east or south, full height r), plus two blocks.
     */
    static BoundingBox caveBox(BlockPos c, int radius, LevelHeightAccessor h) {
        int side = 2 * (radius / 3) + 7;
        int xz = Math.max(radius, radius - 2 + side) + 2;
        int y = Math.max(radius / 2, side) + 2;
        return new BoundingBox(c.m_123341_() - xz, Math.max(h.m_141937_(), c.m_123342_() - y), c.m_123343_() - xz,
                c.m_123341_() + xz, Math.min(h.m_151558_() - 1, c.m_123342_() + y), c.m_123343_() + xz);
    }

}
