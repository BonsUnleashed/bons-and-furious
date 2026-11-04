package bons.furious.patch.immersiveweathering_c49;

import com.ordana.immersive_weathering.data.block_growths.growths.ConfigurableBlockGrowth;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch immersiveweathering_area_scan_table (Immersive Weathering 1.20.1-2.0.5, LGPLv3; server side).
 * Immersive Weathering's AreaCheck.test is carried here in a modified form (LGPLv3 permits it).
 *
 * What Immersive Weathering does. AreaCheck.test ("generate_if_not_too_many", run after a growth passed its chance and
 * target checks) builds WeatheringHelper.grabBlocksAroundRandomly: every position of the radius box in
 * BlockPos.withinManhattan order (125 positions at radius 2, 729 at radius 4) copied through a stream into a list, copied
 * again into an ArrayList and shuffled with new Random(Mth.getSeed(center)), then walks it until a rule decides.
 *
 * What the switch does. The box's offsets in withinManhattan order are computed once per AreaCheck (by the same vanilla
 * iterator around the origin: its order is the same for every centre); each call replays Collections.shuffle (for
 * i = n..2: swap(i - 1, random.nextInt(i)), the RandomAccess branch it takes for an ArrayList) on an index array with a
 * java.util.Random seeded exactly as Immersive Weathering seeds it, and walks the shuffled positions with the same reads,
 * the same RuleTest calls on the same RandomSource, the same counting and the same early returns. Each visited position is
 * a new BlockPos, as in Immersive Weathering's list; only the positions after an early return are never built.
 *
 * -Dbons_and_furious.immersiveweatheringAreaScanTable=false switches it off at run time.
 */
public final class AreaScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.immersiveweatheringAreaScanTable", "true"));
    private static volatile boolean announced;

    private AreaScan() {}

    /** Offsets (dx, dy, dz triples) of the radius box in BlockPos.withinManhattan order. */
    public static int[] offsets(int rX, int rY, int rZ) {
        int n = 0;
        for (BlockPos ignored : BlockPos.m_121925_(BlockPos.f_121853_, rX, rY, rZ)) n++;
        int[] o = new int[n * 3];
        int i = 0;
        for (BlockPos p : BlockPos.m_121925_(BlockPos.f_121853_, rX, rY, rZ)) {
            o[i++] = p.m_123341_();
            o[i++] = p.m_123342_();
            o[i++] = p.m_123343_();
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: immersiveweathering_area_scan_table applies (Immersive Weathering's area checks walk a cached offset table)");
        }
        return o;
    }

    public static boolean test(int[] offsets, BlockPos pos, Level level, ConfigurableBlockGrowth config, int requiredAmount,
                               Optional<Integer> yOffset, Optional<RuleTest> mustHave, Optional<RuleTest> mustNotHave,
                               Optional<HolderSet<Block>> extraIncluded) {
        if (yOffset.isPresent()) pos = pos.m_6630_(yOffset.get());
        int count = 0;
        RandomSource random = RandomSource.m_216335_(Mth.m_14057_(pos));
        boolean hasRequirement = mustHave.isEmpty();
        int n = offsets.length / 3;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Random shuffle = new Random(Mth.m_14057_(pos));
        for (int i = n; i > 1; i--) {
            int j = shuffle.nextInt(i);
            int t = order[i - 1];
            order[i - 1] = order[j];
            order[j] = t;
        }
        int x = pos.m_123341_(), y = pos.m_123342_(), z = pos.m_123343_();
        for (int k = 0; k < n; k++) {
            int b = order[k] * 3;
            BlockPos p = new BlockPos(x + offsets[b], y + offsets[b + 1], z + offsets[b + 2]);
            BlockState state = level.m_8055_(p);
            if (config.getPossibleBlocks().contains(state.m_60734_()) || extraIncluded.isPresent() && state.m_204341_(extraIncluded.get())) count++;
            if (!hasRequirement && mustHave.get().m_213865_(state, random)) {
                hasRequirement = true;
                if (requiredAmount == -1) break;
            } else if (mustNotHave.isPresent() && mustNotHave.get().m_213865_(state, random)) {
                return false;
            }
            if (count >= requiredAmount) return false;
        }
        return hasRequirement;
    }
}
