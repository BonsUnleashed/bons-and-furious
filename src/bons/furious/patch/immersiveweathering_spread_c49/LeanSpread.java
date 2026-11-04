package bons.furious.patch.immersiveweathering_spread_c49;

import com.mojang.datafixers.util.Pair;
import com.ordana.immersive_weathering.blocks.cracked.CrackSpreader;
import com.ordana.immersive_weathering.blocks.mossy.MossSpreader;
import com.ordana.immersive_weathering.util.PatchSpreader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch immersiveweathering_patch_spread_lean (Immersive Weathering 1.20.1-2.0.5, LGPL-3.0; server side
 * incl. the integrated server). Ours; no Immersive Weathering code is carried (a re-implementation of the arithmetic of
 * PatchSpreader's default getWantedWeatheringState, calling the spreader's own methods for everything else).
 *
 * What the mod does. Every random tick and every neighbour update of a moss- or crack-able block (Mossable / Crackable
 * shouldWeather and tryWeather) asks MossSpreader or CrackSpreader.getWantedWeatheringState, a PatchSpreader interface
 * default that builds a java.util.Random seeded from the position, a HashMap of the six directions, a shuffled ArrayList,
 * a float[7], a Pair per direction and an agent list, and recurses into weathering neighbours (depth 2).
 *
 * What the switch does. The same computation without those objects: java.util.Random's LCG replayed on a local long
 * (the same seed scramble, nextFloat and nextInt(bound) with its rejection loop), getDirectionCount's float expression
 * unchanged, Collections.shuffle's swaps on a packed permutation, the same susceptibility draws, then for each direction
 * the spreader's own getBlockWeatheringEffect (so recursion, tags, fluids and the mod's per-spreader effects are its own).
 *
 * Exact variant. The answer is the stock answer for every position and world: every random draw is the stock draw in the
 * stock order, every direction gets the stock susceptibility, and the stock result does not depend on the order in which
 * the six directions are evaluated (air: any direction; always-weather: any direction; PREVENT in any direction wins over
 * WEATHER in any). The difference: the six neighbouring blocks (and the recursion's) are read in the shuffled order instead
 * of the HashMap's iteration order; the same blocks are read, and reading a block changes nothing. Only exactly
 * MossSpreader and CrackSpreader (not subclasses) take this path; they override none of the defaults it relies on
 * (guarded).
 *
 * -Dbons_and_furious.immersiveweatheringPatchSpreadLean=false switches it off at run time.
 */
public final class LeanSpread {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.immersiveweatheringPatchSpreadLean", "true"));
    private static volatile boolean announced;
    private static final long MULT = 0x5DEECE66DL, ADD = 0xBL, MASK = (1L << 48) - 1;
    /** The list Collections.shuffle starts from: new ArrayList(List.of(Direction.values())). */
    private static final Direction[] DIRS = Direction.values();
    private static final PatchSpreader.Susceptibility LOW = PatchSpreader.Susceptibility.LOW, MEDIUM = PatchSpreader.Susceptibility.MEDIUM,
            HIGH = PatchSpreader.Susceptibility.HIGH;

    private LeanSpread() {}

    /** True for exactly the two spreader classes whose defaults this path relies on. */
    public static boolean plain(Object spreader) {
        Class<?> c = spreader.getClass();
        return c == MossSpreader.class || c == CrackSpreader.class;
    }

    private static long step(long s) {
        return (s * MULT + ADD) & MASK;
    }

    /** PatchSpreader's default getWantedWeatheringState(hasTicked, pos, level, maxRecursion), without its temporary objects. */
    public static boolean wanted(PatchSpreader<?> sp, boolean hasTicked, BlockPos pos, Level level, int maxRecursion) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: immersiveweathering_patch_spread_lean applies (Immersive Weathering's moss and crack spread checks run without temporary objects)");
        }
        long s = (Mth.m_14057_(pos) ^ MULT) & MASK;                   // new Random(Mth.getSeed(pos))
        sp.getUnWeatherableChance(level, pos);                         // read first by the default too (value unused there)
        double directionChance = sp.getInterestForDirection(level, pos);
        double highInterestChance = sp.getDisjointGrowthChance(level, pos);
        // getDirectionCount(posRandom, (float) directionChance)
        float a = (float) directionChance;
        s = step(s);
        float r = (int) (s >>> 24) / (float) (1 << 24);
        int wantedDirs = 0;
        for (int x = 0; x <= 6; ++x) {
            float v = -0.023809524f * ((float) x * (12.0f * a - 2.0f * a - 12.0f + 1.0f) + (float) (x * x) * (1.0f - 2.0f * a) + (2.0f * a * 6.0f - 12.0f));
            if (r < v) {
                wantedDirs = x;
                break;
            }
        }
        // Collections.shuffle(new ArrayList(List.of(Direction.values())), posRandom): for i = 6..2 swap(i - 1, nextInt(i));
        // position k of the list holds the ordinal in bits 4k..4k+3
        long perm = 0x543210L;
        for (int i = 6; i > 1; i--) {
            s = step(s);
            int u = (int) (s >>> 17);                                   // next(31)
            int m = i - 1, j;
            if ((i & m) == 0) {
                j = (int) ((i * (long) u) >> 31);
            } else {
                j = u % i;
                while (u - j + m < 0) {
                    s = step(s);
                    u = (int) (s >>> 17);
                    j = u % i;
                }
            }
            int a1 = i - 1;
            long va = (perm >>> (4 * a1)) & 0xF, vb = (perm >>> (4 * j)) & 0xF;
            perm = perm & ~(0xFL << (4 * a1)) & ~(0xFL << (4 * j)) | (vb << (4 * a1)) | (va << (4 * j));
        }
        // susceptibilities in list order: the first wantedDirs draw HIGH / MEDIUM, the rest LOW (packed 2 bits each)
        int sus = 0;
        for (int i = 0; i < 6; i++) {
            int v;
            if (i < wantedDirs) {
                s = step(s);
                v = (double) ((int) (s >>> 24) / (float) (1 << 24)) < highInterestChance ? 3 : 2;
            } else {
                v = 1;
            }
            sus |= v << (2 * i);
        }
        boolean needsAir = sp.needsAirToSpread(level, pos);
        boolean hasAir = false, prevent = false, weather = false;
        for (int i = 0; i < 6; i++) {
            Direction d = DIRS[(int) ((perm >>> (4 * i)) & 0xF)];
            BlockPos facingPos = pos.m_121945_(d);
            BlockState facingState = level.m_8055_(facingPos);
            if (!hasAir && needsAir) hasAir = !facingState.m_60796_(level, pos);
            int v = (sus >>> (2 * i)) & 3;
            Pair<PatchSpreader.WeatheringAgent, Boolean> pair = sp.getBlockWeatheringEffect(v == 3 ? HIGH : v == 2 ? MEDIUM : LOW, facingState, level, facingPos, maxRecursion);
            PatchSpreader.WeatheringAgent agent = pair.getFirst();
            if (agent == PatchSpreader.WeatheringAgent.PREVENT_WEATHERING) prevent = true;
            else if (agent == PatchSpreader.WeatheringAgent.WEATHER) weather = true;
            if (pair.getSecond()) needsAir = false;
        }
        if (needsAir && !hasAir) return false;
        if (hasTicked) {
            s = step(s);
            if ((double) ((int) (s >>> 24) / (float) (1 << 24)) < sp.getUnWeatherableChance(level, pos)) return false;
        }
        return !prevent && weather;
    }
}
