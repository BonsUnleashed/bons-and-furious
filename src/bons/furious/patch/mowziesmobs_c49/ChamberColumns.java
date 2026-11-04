package bons.furious.patch.mowziesmobs_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mowziesmobs_chamber_column_memo (Mowzie's Mobs 1.7.3, 1.8.2 identical; server world generation;
 * since 1.0.49). Idea: Collections Of Optimizations (mowziesmobsWorldgen.shareChamberColumns), idea text only. Our own
 * logic; no Mowzie's Mobs code (custom licence, injectors only).
 *
 * WroughtnautChamberStructure.tryWroughtChamber, run for every structure start attempt of a Ferrous Wroughtnaut chamber,
 * asks ChunkGenerator.getBaseColumn for an 8 x 8 grid of columns (step 2) and again for every step of four 8-block walks
 * from each grid point: up to 64 + 2,048 full-height noise columns, of about 430 distinct (x, z).
 *
 * What changes: within one call of tryWroughtChamber the column of an (x, z) is computed once and handed to every later
 * request for the same (x, z). Why it is identical: getBaseColumn computes the column from the generator's noise for that
 * (x, z), the call's height accessor and random state (the same two objects for every request of one call; a request
 * with other objects is passed on), and tryWroughtChamber only reads the returned columns (NoiseColumn.getBlock).
 *
 * -Dbons_and_furious.mowziesmobsChamberColumnMemo=false computes every column (stock).
 */
public final class ChamberColumns {
    public static final String KEY = "mowziesmobs_chamber_column_memo";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsChamberColumnMemo", "true"));
    private static volatile boolean announced;

    private ChamberColumns() {
    }

    /** Per tryWroughtChamber call: the generator, accessor and random state of its first request, and the columns. */
    public static final class Memo {
        final ChunkGenerator generator;
        final LevelHeightAccessor height;
        final RandomState state;
        final Long2ObjectOpenHashMap<NoiseColumn> columns = new Long2ObjectOpenHashMap<>();

        Memo(ChunkGenerator generator, LevelHeightAccessor height, RandomState state) {
            this.generator = generator;
            this.height = height;
            this.state = state;
        }
    }

    public static NoiseColumn column(ChunkGenerator generator, int x, int z, LevelHeightAccessor height, RandomState state,
                                     Operation<NoiseColumn> original, LocalRef<Memo> memo) {
        if (!enabled) return original.call(generator, x, z, height, state);
        Memo m = memo.get();
        if (m == null) {
            m = new Memo(generator, height, state);
            memo.set(m);
        }
        if (m.generator != generator || m.height != height || m.state != state) return original.call(generator, x, z, height, state);
        long key = (long) x << 32 | (z & 0xFFFFFFFFL);
        NoiseColumn c = m.columns.get(key);
        if (c == null) {
            c = original.call(generator, x, z, height, state);
            m.columns.put(key, c);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (a Wroughtnaut chamber attempt computes each noise column once)", KEY);
            }
        }
        return c;
    }
}
