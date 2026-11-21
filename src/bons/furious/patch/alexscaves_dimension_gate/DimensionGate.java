package bons.furious.patch.alexscaves_dimension_gate;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.slf4j.Logger;

/**
 * Bons and Furious switch alexscaves_rare_biome_dimension_first (Alex's Caves 2.0.2, LGPL-3.0; both sides, it acts wherever
 * terrain is generated). SRG: m_135782_ ResourceKey.location.
 *
 * <p>Alex's Caves injects at the head of MultiNoiseBiomeSource.getNoiseBiome (its MultiNoiseBiomeSourceMixin handler
 * citadel_getNoiseBiomeCoords) and, for every lookup whose quart lies in a rare-biome Voronoi cell, samples the whole
 * climate at the position (for its depth), then for each of its biome conditions samples the climate again at the cell
 * centre, tests five climate ranges and the depth, and only then tests whether the condition lists the dimension the
 * biome source was last given (BiomeGenerationNoiseCondition.test, the last line). Its biomes are overworld-only by
 * default, so in every other multi-noise dimension (the Nether, Cosmic Horizons' and Ad Astra's planets, most modded
 * dimensions) all of that is computed and thrown away. In this pack that was 31.6 % of the Moon's world generation,
 * 20.2 % of Mars and 4.3 % of the Nether (profiles of 2026-11-20).
 *
 * <p>{@link #skip} answers, before the handler's own work, whether NO condition in BiomeGenerationConfig.BIOMES lists the
 * dimension: then no condition can pass (the dimension test is a conjunction with everything else the test computes) and
 * the handler, which only acts by setting a return value when one passes, ends without doing anything. Everything it
 * would have computed first is pure (getRareBiomeInfoForQuad reseeds a generator only it uses before every use; climate
 * samples have no side effect), so skipping it leaves every biome as it was. A dimension the source was not given
 * (null: Alex's Caves then lets any dimension pass), a condition whose list is missing, or any failure keeps the
 * original code. The answer is remembered per biome source, keyed by the dimension key object and a generation that
 * BiomeGenerationConfig.reloadConfig bumps when it returns (the conditions are rebuilt from the config files there).
 *
 * <p>-Dbons_and_furious.acDimensionGate=false keeps the handler as shipped (the config switch acts at class transformation);
 * .metrics=true counts lookups skipped / kept and prints them at JVM exit; .verify=true (rigs) never skips, lets every handler
 * run and counts, at the handler's return, every lookup the switch would have skipped in which the handler set a biome: a
 * run without such a line shows the skip never drops a decision.
 */
public final class DimensionGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.acDimensionGate.verify");
    public static final boolean ACTIVE = !VERIFY && !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.acDimensionGate", "true"));
    public static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.acDimensionGate.metrics");
    /** False when the handler runs exactly as shipped and nothing is counted. */
    public static final boolean ON = ACTIVE || METRICS;
    /** Bumped when BiomeGenerationConfig.reloadConfig returns. */
    public static volatile int generation;
    public static final LongAdder SKIPPED = new LongAdder(), KEPT = new LongAdder(), WRONG = new LongAdder();
    private static volatile boolean announced;

    /** The decision remembered on a biome source (one immutable object, so a racing reader never mixes two decisions). */
    public record Decision(ResourceKey<Level> dimension, int generation, boolean skip) {
    }

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious alexscaves_rare_biome_dimension_first: lookups "
                    + (VERIFY ? "the gate would skip " : "skipped ") + SKIPPED.sum() + ", lookups run as shipped " + KEPT.sum()
                    + (VERIFY ? ", skippable lookups in which Alex's Caves set a biome " + WRONG.sum() : "")),
                    "Bons and Furious Alex's Caves dimension gate summary"));
        }
    }

    private DimensionGate() {
    }

    /** The decision for the source's current dimension; {@code remembered} is the source's last decision (or null). */
    public static Decision decide(Decision remembered, ResourceKey<Level> dimension, Collection<?> conditions, ConditionLists lists) {
        int gen = generation;
        if (remembered != null && remembered.dimension == dimension && remembered.generation == gen) return remembered;
        boolean skip = false;
        if (dimension != null) {
            try {
                String name = dimension.m_135782_().toString();
                skip = true;
                for (Object c : conditions) {
                    List<String> l = lists.dimensions(c);
                    if (l == null || l.contains(name)) {
                        skip = false;
                        break;
                    }
                }
            } catch (Throwable t) {          // a reload running at the same moment, or anything unexpected: keep the original
                skip = false;
            }
        }
        if (skip && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: alexscaves_rare_biome_dimension_first applies (Alex's Caves' rare cave biomes are not tested in {}, "
                    + "where none of its biome conditions lets them appear){}", dimension.m_135782_(), VERIFY ? " - verify mode, nothing skipped" : "");
        }
        return new Decision(dimension, gen, skip);
    }

    /**
     * Alex's Caves' lastSampledDimension on MultiNoiseBiomeSource. Its mixin adds the field, so a mixin of ours cannot @Shadow it
     * (Mixin resolves every shadow of a target's mixins against the class before any of them is applied); a getter handle made
     * once, typed (Object)Object for invokeExact, reads it at the speed of a field read. Null when the field is not there.
     */
    private static final class LastDimension {
        static final MethodHandle GET;

        static {
            MethodHandle h = null;
            try {
                Field f = MultiNoiseBiomeSource.class.getDeclaredField("lastSampledDimension");
                if (f.getType() == ResourceKey.class && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    h = MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(Object.class, Object.class));
                }
            } catch (Throwable t) {
                h = null;
            }
            GET = h;
        }
    }

    /** The dimension Alex's Caves last gave this biome source; null when it gave none or the field cannot be read (the gate then keeps the original). */
    @SuppressWarnings("unchecked")
    public static ResourceKey<Level> lastDimension(Object source) {
        MethodHandle h = LastDimension.GET;
        if (h == null) return null;
        try {
            Object v = (Object) h.invokeExact(source);
            return v instanceof ResourceKey<?> k ? (ResourceKey<Level>) k : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Reads a condition's dimension list (an accessor mixin in the game, a lambda in tests). */
    @FunctionalInterface
    public interface ConditionLists {
        List<String> dimensions(Object condition);
    }

    /** Counts one lookup (metrics only). */
    public static void count(boolean skip) {
        if (METRICS) (skip ? SKIPPED : KEPT).increment();
    }

    /** Verify mode, at the handler's return: the handler set a biome in a lookup the gate would have skipped. */
    public static void wrong(ResourceKey<Level> dimension, int x, int y, int z) {
        WRONG.increment();
        LOGGER.error("Bons and Furious: alexscaves_rare_biome_dimension_first would have skipped a lookup at quart {} {} {} in {}, but Alex's Caves set a biome there",
                x, y, z, dimension == null ? "null" : dimension.m_135782_());
    }
}
