package bons.furious.patch.fluidwalk_c2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bons and Furious switch lionfishapi_fluid_walk_scan (Lionfish API 2.8 / 3.0, LGPL; both sides): the allocation-free version
 * of Lionfish's fluid-walk scan loop. Lionfish API is LGPL, so this modified version of its logic may be carried in our
 * GPL source (patches/fluidwalk_c2.json "license"). Relics' handler (All Rights Reserved) is NOT re-implemented anywhere:
 * relics_fluid_walk_scan only skips it where its cells hold no fluid (RelicsFluidWalk).
 *
 * Lionfish's handler runs inside Entity.move for every living entity that is not moving up. It built a fresh 12-entry
 * offset table (13 arrays) and one BlockPos per offset on every call, then asked Level.getFluidState for each cell, and
 * for a non-empty fluid compared the height of a block-shaped box raised by the fluid's own height against the best so
 * far (strict "<", first best wins). This loop visits the same offsets in the same order, re-reads Entity.blockPosition()
 * in every iteration like the handler, computes the same cell with int arithmetic, hands Level.getFluidState the
 * entity's cursor instead of a new BlockPos, and runs the identical shape test and comparison on the same values.
 *
 * Lionfish's offsets are 0.5 / 0 / -0.5 / -1 added to an int and floored by BlockPos.containing: floor(x + 0.5) = x,
 * floor(x - 0.5) = x - 1 and floor(y - 1.0) = y - 1 for every int (doubles hold ints exactly; at Integer.MIN_VALUE both
 * forms wrap to Integer.MAX_VALUE), so its 12 offsets are only 6 cells. A repeated cell is skipped only when that is
 * provably invisible:
 *  - the entity's block position object is the one read in the first iteration (Entity replaces it on every block
 *    change), so the repeat names the same cell;
 *  - the read happens on the level's own thread of a ServerLevel (whose chunk source answers a second read of the chunk
 *    it has just returned from its cache, without tickets or loading) or on a client level (pure reads);
 *  - the cell's z is not on a chunk's low edge (z & 15 != 0). Then the 12 reads touch at most two chunk columns, every
 *    repeat directly follows a read of its own chunk, and nothing (no chunk load, no listener) can run between a cell's
 *    first read and its repeat: the repeat would be a cache hit returning the same state. A repeat can never pass the
 *    strict "<" a second time, so the best height and fluid are unchanged. When z & 15 == 0 the original visits chunk A,
 *    then B, then A again, so a load of B could evict A; those calls (1 in 16) read all 12 cells, still without allocating.
 * The first read of every chunk keeps the original's order, so chunk loading, tickets and caches see the same calls.
 */
public final class FluidWalkScan {
    /** Lionfish API's offsets (2.8 / 3.0) in its order, as the integer cell offsets BlockPos.containing produces. */
    static final int[] LIONFISH_DX = {0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1};
    static final int[] LIONFISH_DY = {0, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1, 0};
    static final int[] LIONFISH_DZ = {0, 0, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1};
    /** True where the offset names a cell an earlier offset already named (first-occurrence order 1, 3, 4, 9, 11, 12). */
    static final boolean[] LIONFISH_REPEAT = {false, true, false, false, true, true, true, true, false, true, false, false};

    private FluidWalkScan() {
    }

    /**
     * The handlers' loop. Returns the best fluid (null when none) and leaves the best height in cursor.highestValue.
     * startValue is the handler's original.y.
     */
    static FluidState scan(LivingEntity entity, Level level, double startValue, FluidWalkCursor cursor,
                           int[] dx, int[] dy, int[] dz, boolean[] repeat) {
        double highestValue = startValue;
        FluidState highestFluid = null;
        BlockPos first = null;
        boolean skipRepeats = false;
        for (int i = 0; i < 12; i++) {
            BlockPos source = entity.m_20183_();
            if (i == 0) {
                first = source;
                skipRepeats = canSkipRepeats(level, source.m_123343_());
            } else if (source != first) {
                skipRepeats = false;   // the entity changed block mid-scan: read every remaining offset as the original does
            }
            if (skipRepeats && repeat[i]) continue;
            int x = source.m_123341_() + dx[i];
            int y = source.m_123342_() + dy[i];
            int z = source.m_123343_() + dz[i];
            FluidState fluidState = level.m_6425_(cursor.m_122178_(x, y, z));
            if (fluidState.m_76178_()) continue;
            VoxelShape shape = Shapes.m_83144_().m_83216_((double) x, (double) ((float) y + fluidState.m_76182_()), (double) z);
            if (!Shapes.m_83157_(shape, Shapes.m_83064_(entity.m_20191_().m_82400_(0.5)), BooleanOp.f_82689_)) continue;
            double height = shape.m_83297_(Direction.Axis.Y) - entity.m_20186_() - 1.0;
            if (!(highestValue < height)) continue;
            highestValue = height;
            highestFluid = fluidState;
        }
        cursor.highestValue = highestValue;
        return highestFluid;
    }

    /** See the class comment: repeats are skipped only where a repeated read is provably a pure cache hit. */
    static boolean canSkipRepeats(Level level, int z) {
        if ((z & 15) == 0) return false;
        if (level.m_5776_()) return true;
        return level instanceof ServerLevel server && server.m_7654_().m_18695_();
    }

    /**
     * Lionfish's original loop shape for the shadow check (LGPL): BlockPos.containing of the double offsets, a new
     * BlockPos per offset, every offset read. Returns the best fluid and writes the best height into out[0].
     */
    static FluidState reference(LivingEntity entity, Level level, double startValue, double[][] doubleOffsets, double[] out) {
        double highestValue = startValue;
        FluidState highestFluid = null;
        for (int i = 0; i < doubleOffsets.length; i++) {
            BlockPos source = entity.m_20183_();
            BlockPos pos = BlockPos.m_274561_(source.m_123341_() + doubleOffsets[i][0], source.m_123342_() + doubleOffsets[i][1],
                    source.m_123343_() + doubleOffsets[i][2]);
            FluidState fluidState = level.m_6425_(pos);
            if (fluidState.m_76178_()) continue;
            VoxelShape shape = Shapes.m_83144_().m_83216_((double) pos.m_123341_(), (double) ((float) pos.m_123342_() + fluidState.m_76182_()),
                    (double) pos.m_123343_());
            if (!Shapes.m_83157_(shape, Shapes.m_83064_(entity.m_20191_().m_82400_(0.5)), BooleanOp.f_82689_)) continue;
            double height = shape.m_83297_(Direction.Axis.Y) - entity.m_20186_() - 1.0;
            if (!(highestValue < height)) continue;
            highestValue = height;
            highestFluid = fluidState;
        }
        out[0] = highestValue;
        return highestFluid;
    }
}
