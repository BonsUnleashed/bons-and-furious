package bons.furious.patch.alexscaves;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch alexscaves_rare_biome_column_memo (Alex's Caves 2.0.2, LGPL; server side of world generation).
 *
 * Alex's Caves hooks every overworld noise-biome lookup (MultiNoiseBiomeSource.getNoiseBiome, its own mixin) and first
 * asks ACBiomeRarity.getRareBiomeInfoForQuad(worldSeed, quartX, quartZ): reseed the shared Voronoi generator, two simplex
 * noise samples, two config reads and a 3x3 Voronoi cell search with its allocations. The answer does not depend on y,
 * yet a chunk's biome fill asks it for every quart of every section: 3,072 calls per 768-high chunk for the same 16
 * (x, z) quarts (the fill walks z fastest inside each section, so the previous answer alone would never repeat).
 *
 * The wrapped method keeps, per thread, a 64-entry table indexed by the low three bits of x and z, keyed by the full
 * (seed, x, z), the raw bits of the caveBiomeWidthRandomness value read this call, and a generation that ACBiomeRarity.init
 * bumps (it sets the Voronoi spacing randomness and the mean width / separation the method reads). On a key match the
 * table answers with the object the original returned for that key (a VoronoiInfo record, or null for "no rare biome
 * here"); AC only reads it. The skipped Voronoi reseed is to the same seed and only this method uses that generator, which
 * it always reseeds before use. Every miss runs the original method.
 *
 * -Dbons_and_furious.rareBiomeColumnMemo=false runs the original every time; -Dbons_and_furious.rareBiomeColumnMemo.shadow=true
 * (verification runs only) also runs it on every hit and counts answers that differ (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class RareBiomeColumns {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.rareBiomeColumnMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.rareBiomeColumnMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Bumped at the end of ACBiomeRarity.init (config values the method reads through statics). */
    public static volatile int generation;
    private static volatile boolean announced;

    private static final ThreadLocal<Table> TABLES = ThreadLocal.withInitial(Table::new);

    private static final class Table {
        final long[] seed = new long[64];
        final long[] width = new long[64];
        final int[] x = new int[64], z = new int[64], gen = new int[64];
        final boolean[] full = new boolean[64];
        final Object[] value = new Object[64];
    }

    private RareBiomeColumns() {
    }

    /** ACBiomeRarity.getRareBiomeInfoForQuad(worldSeed, x, z); widthRandomness = caveBiomeWidthRandomness as the original reads it. */
    public static Object info(long worldSeed, int x, int z, double widthRandomness, Operation<Object> original) {
        if (!enabled) {
            return original.call(worldSeed, x, z);
        }
        Table t = TABLES.get();
        int i = ((x & 7) << 3) | (z & 7);
        long width = Double.doubleToRawLongBits(widthRandomness);
        int gen = generation;
        if (t.full[i] && t.x[i] == x && t.z[i] == z && t.seed[i] == worldSeed && t.width[i] == width && t.gen[i] == gen) {
            Object v = t.value[i];
            if (SHADOW) {
                Object fresh = original.call(worldSeed, x, z);
                SHADOW_CHECKS.incrementAndGet();
                if (!java.util.Objects.equals(fresh, v) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                    LOGGER.warn("Bons and Furious: alexscaves_rare_biome_column_memo shadow mismatch at quart {},{}: {} vs {}", x, z, v, fresh);
                }
            }
            return v;
        }
        Object v = original.call(worldSeed, x, z);
        t.full[i] = true;
        t.x[i] = x;
        t.z[i] = z;
        t.seed[i] = worldSeed;
        t.width[i] = width;
        t.gen[i] = gen;
        t.value[i] = v;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: alexscaves_rare_biome_column_memo applies (Alex's Caves' rare cave biome placement is worked out once per quart column){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return v;
    }
}
