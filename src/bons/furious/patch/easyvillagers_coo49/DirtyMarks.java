package bons.furious.patch.easyvillagers_coo49;

import bons.pure.config.PureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch easyvillagers_dirty_mark_interval (Easy Villagers 1.20.1-1.1.39; kind change, off by
 * default; helper of the group's four mixins; no Easy Villagers code carried).
 *
 * What Easy Villagers does. TraderTileentityBase.tickServer (trader and auto trader), FarmerTileentity.tickServer and
 * BreederTileentity.tickServer (server) and IronFarmTileentity.tick (both sides) call setChanged() on every tick while a
 * villager is inside, whether anything else changed or not (the villager's age counter, advanceAge, moves by one every
 * tick; the iron farm also counts its timer). setChanged marks the chunk unsaved and runs Forge's comparator update
 * (updateNeighbourForOutputSignal: reads and notifies the horizontal neighbours, through conductors a second ring).
 * The other setChanged calls of those block entities (on real work: a crop aged, a trade, an inventory change, a
 * workstation set) are not touched.
 *
 * What the switch does. That one per-tick call runs only on the block's mark ticks: (game time + an offset from the
 * block position) a multiple of N (value line easyvillagers_dirty_mark_interval.value, default 20 = Collections Of
 * Optimizations' default, range 1-200; 1 = every tick). Trade-off: comparators and other neighbours that listen for
 * these blocks' updates hear the idle ones up to N-1 ticks late, and a chunk saved or unloaded between two marks without
 * any other change can keep the villager's age counter (and the iron farm's timer) up to N-1 ticks behind (the save
 * format does not change). Runtime flag bons_and_furious.easyvillagersDirtyMarkInterval (false = every tick).
 */
public final class DirtyMarks {
    public static final String KEY = "easyvillagers_dirty_mark_interval";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.easyvillagersDirtyMarkInterval", "true"));
    /** Statistics for probes (plain fields): marks made and left out. */
    public static long marked, skipped;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private DirtyMarks() {
    }

    /** The value, read on first use of a wrapper (only reached while the switch is on). */
    private static final class Interval {
        static final int N = PureConfig.intValue(KEY, 20, 1, 200);
    }

    /** For the offline proof: the value in effect. */
    public static int interval() {
        return Interval.N;
    }

    /** The wrapped per-tick setChanged() of {@code be}: true = make the call (stock), false = leave it out this tick. */
    public static boolean mark(BlockEntity be) {
        if (!enabled) return true;
        Level level = be.m_58904_();
        int n = Interval.N;
        if (level == null || n <= 1) return true;
        if (markTick(level.m_46467_(), be.m_58899_(), n)) {
            marked++;
            return true;
        }
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {}: Easy Villagers blocks mark themselves changed every {} ticks while idle (each with its own offset)", KEY, n);
        }
        return false;
    }

    /** The block's mark ticks: (game time + offset) is a multiple of n; the offset comes from the position (0..n-1). */
    public static boolean markTick(long gameTime, BlockPos pos, int n) {
        return Math.floorMod(gameTime + offset(pos, n), (long) n) == 0;
    }

    /** The block's own offset in 0..n-1, from its packed position. */
    public static int offset(BlockPos pos, int n) {
        long p = pos.m_121878_();
        int h = (int) (p ^ (p >>> 32)) * 0x9E3779B9;
        return Math.floorMod(h ^ (h >>> 16), n);
    }
}
