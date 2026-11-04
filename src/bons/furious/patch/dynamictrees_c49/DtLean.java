package bons.furious.patch.dynamictrees_c49;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.ferreusveritas.dynamictrees.block.leaves.LeavesProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch dynamictrees_leaf_site_single_read (Dynamic Trees 1.20.1-1.4.11, MIT; world generation and leaf
 * ticks, both sides), exact variant. Idea: Collections Of Optimizations (dynamictrees.leanLeafPlacement), idea text only.
 * Both bodies here are modified copies of Dynamic Trees' own methods (MIT).
 *
 * DynamicLeavesBlock.isLocationSuitableForNewLeaves is asked for up to six neighbours of every ageing leaf (world
 * generation ages every tree's leaves several times): it reads the target block, the block below, the block below again,
 * then the target again through isEmptyBlock and, when that is not air, a third time for canBeReplaced. site() reads each
 * once: the second read of the block below is the first one (nothing is written in between); after the tall grass / large
 * fern replacement the target is read again as shipped, otherwise isEmptyBlock is answered from the first read when the
 * level's isEmptyBlock is LevelReader's own (getBlockState(pos).isAir(); checked once per level class) and canBeReplaced
 * from the same read. light() (hasAdequateLight) stops the smother count at the first position above that is not a tree
 * part: the count can then no longer reach the limit, so the answer is the same. One body for every level class (Distant
 * Horizons' generation region too); hasAdequateLight is still called virtually. The variant: fewer block reads; every
 * answer, every write and their order are the same.
 *
 * Flag: -Dbons_and_furious.dynamictreesLeafSiteSingleRead=false (the shipped bodies run). No shadow mode: site() writes
 * (the tall-plant replacement), so the two bodies cannot run side by side; the offline proof compares with the
 * untransformed class instead.
 */
public final class DtLean {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean leafSite = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dynamictreesLeafSiteSingleRead", "true"));
    private static volatile boolean leafAnnounced;

    private DtLean() {
    }

    /** Per level class: is its isEmptyBlock LevelReader's default (getBlockState(pos).isAir())? */
    private static final ClassValue<Boolean> DEFAULT_EMPTY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return c.getMethod("m_46859_", BlockPos.class).getDeclaringClass() == LevelReader.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };
    private static volatile Class<?> lastDefaultEmpty;

    static boolean defaultEmpty(Object level) {
        Class<?> c = level.getClass();
        if (c == lastDefaultEmpty) return true;
        if (!DEFAULT_EMPTY.get(c)) return false;
        lastDefaultEmpty = c;
        return true;
    }

    /** isLocationSuitableForNewLeaves with single reads (self = the leaves block asked; hasAdequateLight stays virtual). */
    public static boolean site(DynamicLeavesBlock self, LevelAccessor level, LeavesProperties props, BlockPos pos) {
        if (!leafAnnounced) {
            leafAnnounced = true;
            LOGGER.info("Bons and Furious: dynamictrees_leaf_site_single_read applies (Dynamic Trees reads each block of a new leaf site once)");
        }
        BlockState blockState = level.m_8055_(pos);
        Block block = blockState.m_60734_();
        if (block instanceof DynamicLeavesBlock) return false;
        BlockState below = level.m_8055_(pos.m_7495_());
        if (!props.canGrowOnGround() && (below.m_60815_() && !TreeHelper.isBranch(below) && !(below.m_60734_() instanceof LeavesBlock)
                || below.m_60734_() instanceof LiquidBlock)) return false;
        // the shipped method reads the block below a second time here; nothing was written since, so it is the same state
        boolean open;
        if (block instanceof DoublePlantBlock && blockState.m_61143_(DoublePlantBlock.f_52858_) == DoubleBlockHalf.UPPER
                && below.m_60734_() instanceof DoublePlantBlock && below.m_61143_(DoublePlantBlock.f_52858_) == DoubleBlockHalf.LOWER) {
            if (block == Blocks.f_50359_) level.m_7731_(pos.m_7495_(), Blocks.f_50034_.m_49966_(), 3);
            else if (block == Blocks.f_50360_) level.m_7731_(pos.m_7495_(), Blocks.f_50035_.m_49966_(), 3);
            level.m_7471_(pos, false);
            open = level.m_46859_(pos) || level.m_8055_(pos).m_247087_();       // written: read again, as shipped
        } else if (defaultEmpty(level)) {
            open = blockState.m_60795_() || blockState.m_247087_();
        } else {
            open = level.m_46859_(pos) || blockState.m_247087_();
        }
        return open && self.hasAdequateLight(blockState, level, props, pos);
    }

    /** hasAdequateLight, the smother count stopped at the first position above that is not a tree part. */
    public static boolean light(BlockState state, LevelAccessor level, LeavesProperties props, BlockPos pos) {
        if (level.m_46861_(pos)) return true;
        int smother = props.getSmotherLeavesMax();
        if (smother != 0 && DynamicLeavesBlock.isBottom(level, pos)) {
            int smotherLeaves = 0;
            for (int i = 0; i < smother; ++i) {
                if (!TreeHelper.isTreePart(level, pos.m_6630_(i + 1))) break;       // the limit can no longer be reached
                smotherLeaves++;
            }
            if (smotherLeaves >= smother) return false;
        }
        return level.m_45517_(LightLayer.SKY, pos) >= (TreeHelper.isLeaves(state) ? props.getLightRequirement() - 2 : props.getLightRequirement());
    }
}
