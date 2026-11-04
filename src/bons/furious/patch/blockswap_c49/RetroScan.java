package bons.furious.patch.blockswap_c49;

import corgitaco.blockswap.config.BlockSwapConfig;
import corgitaco.blockswap.swapper.Swapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch blockswap_retrogen_palette_scan (Block Swap 5.0.0.0, LGPLv3; server). Ours: the retro-gen walk
 * is re-written around the same per-position step (Block Swap's own Swapper.remapState and world.setBlock calls).
 *
 * What Block Swap does. With retro_gen on, the first time a loaded chunk broadcasts its changes Swapper.runRetroGenerator
 * visits all 4,096 positions of every section (x, then y, then z): a new BlockPos, world.getBlockState, then a swap
 * (world.setBlock, flag 2) when the block is a key of the block map, and another when the state is a key of the state map
 * (both decided on the state read first). Most sections hold no swap target at all, and the walk only reads there.
 *
 * What the switch does. For each section, the states actually present in its storage (PalettedContainer.count) are
 * checked against both maps first; a section holding none is skipped (Block Swap would only read there), a section whose
 * only content is air uses the air Block Swap's chunk read returns. In a section that can match, positions are visited in
 * Block Swap's order with a direct section read; only where that state matches does Block Swap's exact step run (new
 * BlockPos, world.getBlockState, the same two decisions and setBlock calls). After the first setBlock of a section the
 * rest of that section runs Block Swap's step at every position (a placed block may change other blocks), and every
 * later section is checked when it is reached, so it sees all earlier changes.
 *
 * Why identical. The walk applies only when world.getChunk(chunk x, z) is this very chunk, the sections array is the
 * chunk's own and the level is not the debug world: then world.getBlockState(pos) of every position of the column is the
 * chunk's read, which returns the section's stored state (air for a section with only air). Positions that are skipped
 * hold no state either map knows, and Block Swap does nothing there but read; every setBlock happens at the same position,
 * with the same state, in the same order. Not eligible: Block Swap's own walk.
 *
 * -Dbons_and_furious.blockswapRetrogenPaletteScan=false switches it off at run time. No shadow mode: the walk writes the
 * world, so both walks cannot run on one chunk; the offline proof compares them on twin chunks.
 */
public final class RetroScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.blockswapRetrogenPaletteScan", "true"));
    public static final java.util.concurrent.atomic.AtomicLong SKIPPED_SECTIONS = new java.util.concurrent.atomic.AtomicLong();
    private static volatile boolean announced;

    private RetroScan() {
    }

    static boolean matches(BlockState s, Map<Block, Block> blocks, Map<BlockState, BlockState> states) {
        return blocks.containsKey(s.m_60734_()) || states.containsKey(s);
    }

    /**
     * Runs the retro-gen walk of this chunk when it is eligible (see the class comment) and returns true; returns false,
     * having done nothing, when Block Swap's own walk must run. Called where Block Swap has decided the walk is due.
     */
    public static boolean run(Level world, LevelChunkSection[] sections, LevelChunk chunk, BlockSwapConfig config) {
        if (!enabled || world == null || chunk == null || sections == null) return false;
        if (world.m_46659_() || sections != chunk.m_7103_()) return false;
        int cx = chunk.m_7697_().f_45578_, cz = chunk.m_7697_().f_45579_;
        if (world.m_6325_(cx, cz) != chunk) return false;
        Map<Block, Block> blocks = config.blockBlockMap();
        Map<BlockState, BlockState> states = config.blockStateBlockStateMap();
        BlockState air = Blocks.f_50016_.m_49966_();
        boolean airMatches = matches(air, blocks, states);
        List<BlockState> found = new ArrayList<>(4);
        for (int i = 0; i < sections.length; ++i) {
            LevelChunkSection section = sections[i];
            if (section == null) continue;
            int bottomY = SectionPos.m_123223_(chunk.m_151568_(i));
            if (section.m_188008_()) {                       // only air: Block Swap reads AIR at every position
                if (airMatches) walk(world, chunk, config, bottomY, 0);
                else SKIPPED_SECTIONS.incrementAndGet();
                continue;
            }
            found.clear();
            section.m_63019_().m_63099_((state, count) -> {
                if (matches(state, blocks, states)) found.add(state);
            });
            if (found.isEmpty()) {
                SKIPPED_SECTIONS.incrementAndGet();
                continue;
            }
            int start = 0;
            for (; start < 4096; start++) {
                int x = start >> 8, y = (start >> 4) & 15, z = start & 15;
                BlockState s = section.m_62982_(x, y, z);
                if (!contains(found, s)) continue;
                if (step(world, chunk, config, bottomY, x, y, z)) {
                    start++;
                    walk(world, chunk, config, bottomY, start);  // something was placed: Block Swap's step everywhere after
                    break;
                }
            }
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: blockswap_retrogen_palette_scan applies (Block Swap's retro-gen skips sections that hold nothing to swap)");
        }
        return true;
    }

    private static boolean contains(List<BlockState> found, BlockState s) {
        for (int i = 0, n = found.size(); i < n; i++) if (found.get(i) == s) return true;
        return false;
    }

    /** Block Swap's step at every position of a section from index `from` (x-major order, as its loops). */
    private static void walk(Level world, LevelChunk chunk, BlockSwapConfig config, int bottomY, int from) {
        for (int k = from; k < 4096; k++) step(world, chunk, config, bottomY, k >> 8, (k >> 4) & 15, k & 15);
    }

    /** Block Swap's step at one position: true when it placed a block. */
    private static boolean step(Level world, LevelChunk chunk, BlockSwapConfig config, int bottomY, int x, int y, int z) {
        BlockPos blockPos = new BlockPos(SectionPos.m_123223_(chunk.m_7697_().f_45578_) + x, bottomY + y, SectionPos.m_123223_(chunk.m_7697_().f_45579_) + z);
        BlockState state = world.m_8055_(blockPos);
        boolean placed = false;
        if (config.blockBlockMap().containsKey(state.m_60734_())) {
            world.m_7731_(blockPos, Swapper.remapState(state), 2);
            placed = true;
        }
        if (config.blockStateBlockStateMap().containsKey(state)) {
            world.m_7731_(blockPos, config.blockStateBlockStateMap().get(state), 2);
            placed = true;
        }
        return placed;
    }
}
