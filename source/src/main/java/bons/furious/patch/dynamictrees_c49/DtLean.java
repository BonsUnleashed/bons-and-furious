package bons.furious.patch.dynamictrees_c49;

import bons.furious.mixin.dynamictrees_c49.LeavesPropertiesWaterAccessor;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import com.dtteam.dynamictrees.tree.TreeHelper;
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
 * Bons and Furious switch dynamictrees_leaf_site_single_read (Dynamic Trees, MIT; 1.21.1 tested build:
 * dynamictrees-neoforge-1.21.1-1.7.2; world generation and leaf ticks, both sides), exact variant. Idea: Collections Of
 * Optimizations (dynamictrees.leanLeafPlacement), idea text only. Both bodies here are modified copies of Dynamic Trees' own
 * methods (MIT).
 *
 * DynamicLeavesBlock.isLocationSuitableForNewLeaves is asked for up to six neighbours of every ageing leaf (world
 * generation ages every tree's leaves several times): it reads the target block (and tests its fluid against the block's
 * own LeavesProperties' water resistance), the block below, the block below again, then the target again through
 * isEmptyBlock and, when that is not air, a third time for canBeReplaced. site() reads each once: the second read of the
 * block below is the first one (nothing is written in between); after the tall grass / large fern replacement the target is
 * read again as shipped, otherwise isEmptyBlock is answered from the first read when the level's isEmptyBlock is
 * LevelReader's own (getBlockState(pos).isAir(); checked once per level class) and canBeReplaced from the same read.
 * light() (hasAdequateLight) stops the smother count at the first position above that is not a tree part: the count can
 * then no longer reach the limit, so the answer is the same. One body for every level class (Distant Horizons' generation
 * region too); hasAdequateLight is still called virtually. The variant: fewer block reads; every answer, every write and
 * their order are the same.
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
                return c.getMethod("isEmptyBlock", BlockPos.class).getDeclaringClass() == LevelReader.class;
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
        BlockState blockState = level.getBlockState(pos);
        Block block = blockState.getBlock();
        if (block instanceof DynamicLeavesBlock) return false;
        if (!blockState.getFluidState().isEmpty() && !((LeavesPropertiesWaterAccessor) self.properties).bons$waterResistant()) return false;
        BlockState below = level.getBlockState(pos.below());
        if (!props.canGrowOnGround() && (below.canOcclude() && !TreeHelper.isBranch(below) && !(below.getBlock() instanceof LeavesBlock)
                || below.getBlock() instanceof LiquidBlock)) return false;
        // the shipped method reads the block below a second time here; nothing was written since, so it is the same state
        boolean open;
        if (block instanceof DoublePlantBlock && blockState.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
                && below.getBlock() instanceof DoublePlantBlock && below.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            if (block == Blocks.TALL_GRASS) level.setBlock(pos.below(), Blocks.SHORT_GRASS.defaultBlockState(), 3);
            else if (block == Blocks.LARGE_FERN) level.setBlock(pos.below(), Blocks.FERN.defaultBlockState(), 3);
            level.removeBlock(pos, false);
            open = level.isEmptyBlock(pos) || level.getBlockState(pos).canBeReplaced();       // written: read again, as shipped
        } else if (defaultEmpty(level)) {
            open = blockState.isAir() || blockState.canBeReplaced();
        } else {
            open = level.isEmptyBlock(pos) || blockState.canBeReplaced();
        }
        return open && self.hasAdequateLight(blockState, level, props, pos);
    }

    /** hasAdequateLight, the smother count stopped at the first position above that is not a tree part. */
    public static boolean light(BlockState state, LevelAccessor level, LeavesProperties props, BlockPos pos) {
        if (level.canSeeSkyFromBelowWater(pos)) return true;
        int smother = props.getSmotherLeavesMax();
        if (smother != 0 && DynamicLeavesBlock.isBottom(level, pos)) {
            int smotherLeaves = 0;
            for (int i = 0; i < smother; ++i) {
                if (!TreeHelper.isTreePart(level, pos.above(i + 1))) break;       // the limit can no longer be reached
                smotherLeaves++;
            }
            if (smotherLeaves >= smother) return false;
        }
        return level.getBrightness(LightLayer.SKY, pos) >= (TreeHelper.isLeaves(state) ? props.getLightRequirement() - 2 : props.getLightRequirement());
    }
}
