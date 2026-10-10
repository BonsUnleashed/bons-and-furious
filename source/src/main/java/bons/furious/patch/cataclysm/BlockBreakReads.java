package bons.furious.patch.cataclysm;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cataclysm_boss_block_breaking (L_Ender's Cataclysm, server, since 1.0.36; 1.21.1 tested build:
 * L_Ender's Cataclysm 1.21.1-3.33, L_Ender's Cataclysm 1.21.1-3.33.jar). Ours; no Cataclysm code (CC-BY-NC-ND-4.0): the
 * mixins only wrap the level calls inside Cataclysm's unchanged loops.
 *
 * The Leviathan's dimensional rift runs a 31 x 31 x 31 loop every tick it lives (29,791 positions): for each position it
 * reads the block, the block above and the block entity, then tests (above is air or water) && block is not air && not
 * immune && no block entity && random.nextInt(2000) == 0. The Netherite Monstrosity's berserk breaking reads the block
 * and the block entity at every position of its loop (2,601 per call) and uses the block entity only for non-air blocks.
 * Per entity, one BlockBreakReads keeps the last block read and (rift) the last "block above" read:
 *  - rift: the block read at (x, y + 1, z) is the "block above" the previous iteration read at that same position; it is
 *    handed out again while nothing in between can have changed the level: every removeBlock / setBlock / addFreshEntity
 *    in the loop clears it, and so does every getBlockEntity call that returns a block entity or is made at a block that
 *    has one (the only calls that can create or load a block entity, whose own code then runs). Every other read asks
 *    the level.
 *  - both: getBlockEntity at a position whose block (just read) is air is not asked; the loop's own test is false there
 *    before the block entity is used, so the loop does exactly what it did (same blocks, order and random draws).
 * Exact variant: a level's getBlockEntity may also drop a stale removed or packed block-entity entry it finds at that
 * (air) position; those clean-ups are not made at air positions from these loops. (A packed entry at a block without a
 * block entity whose load fails returns nothing; the rift does not treat that call as a change.)
 *
 * Ported to 1.21.1: Level.getBlockEntity / LevelChunk.getBlockEntity(pos, IMMEDIATE) keep 1.20.1's semantics (stale entry
 * removal, pending-tag promotion, creation only where the state has a block entity), and both loops are unchanged
 * (NeoForge's EventHooks.canEntityGrief replaces Forge's mobGriefing event). Cataclysm 3.33's Monstrosity BlockBreaking
 * already reads the block entity only after its air/immune check, so it is no longer wrapped; the old Netherite
 * Monstrosity no longer exists.
 *
 * -Dbons_and_furious.cataclysmBossBlockBreaking=false reads everything as before.
 */
public final class BlockBreakReads {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmBossBlockBreaking", "true"));
    private static volatile boolean announced;

    private BlockPos lastPos;
    private BlockState lastState;
    private boolean aboveKept;
    private int aboveX, aboveY, aboveZ;
    private BlockState aboveState;

    public void reset() {
        this.lastPos = null;
        this.lastState = null;
        this.aboveKept = false;
        this.aboveState = null;
    }

    /** A block written or an entity added: the kept "block above" may no longer be what the level holds. */
    public void written() {
        this.aboveKept = false;
        this.aboveState = null;
    }

    /** The rift's block read (getBlockState ordinal 0). */
    public BlockState block(Level level, BlockPos pos, Operation<BlockState> original) {
        BlockState s;
        if (enabled && this.aboveKept && pos.getX() == this.aboveX && pos.getY() == this.aboveY && pos.getZ() == this.aboveZ) {
            s = this.aboveState;
            announce();
        } else {
            s = original.call(level, pos);
        }
        this.lastPos = pos;
        this.lastState = s;
        return s;
    }

    /** The rift's "block above" read (getBlockState ordinal 1). */
    public BlockState above(Level level, BlockPos pos, Operation<BlockState> original) {
        BlockState s = original.call(level, pos);
        this.aboveKept = true;
        this.aboveX = pos.getX();
        this.aboveY = pos.getY();
        this.aboveZ = pos.getZ();
        this.aboveState = s;
        return s;
    }

    /** The Monstrosity's block read: only remembered. */
    public BlockState read(Level level, BlockPos pos, Operation<BlockState> original) {
        BlockState s = original.call(level, pos);
        this.lastPos = pos;
        this.lastState = s;
        return s;
    }

    /** getBlockEntity(pos) after the block at pos was read: not asked when that block makes the loop's test false. */
    public BlockEntity blockEntity(Level level, BlockPos pos, Operation<BlockEntity> original, boolean anyAir) {
        if (enabled && pos == this.lastPos && this.lastState != null
                && (anyAir ? this.lastState.isAir() : this.lastState == Blocks.AIR.defaultBlockState())) {
            announce();
            return null;
        }
        BlockEntity found = original.call(level, pos);
        if (found != null || pos != this.lastPos || this.lastState == null || this.lastState.hasBlockEntity()) {
            written();   // a block entity may have been created or loaded here: its code may have changed the level
        }
        return found;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cataclysm_boss_block_breaking applies (rift and Monstrosity block breaking read each block once and skip block entities at air)");
        }
    }
}
