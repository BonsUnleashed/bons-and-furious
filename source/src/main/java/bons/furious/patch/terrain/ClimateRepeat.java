package bons.furious.patch.terrain;

import bons.pure.terrain.DensityAudit;
import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.slf4j.Logger;

/**
 * Bons and Furious switches vanilla_climate_sample_repeat and vanilla_climate_search_repeat (vanilla 1.20.1 world
 * generation). SRG member names.
 *
 * In this pack one biome lookup samples the climate at the same quart several times in a row: Alex's Caves' biome
 * conditions, ElysiumAPI's TerraBlender helper, TerraBlender and the vanilla lookup each call Climate.Sampler.sample
 * (sample) with the same coordinates, and then search a climate tree (Climate.RTree.search, search) with the same
 * target. While generating this pack 32.8% of the samples and 30.7% of the searches repeated the previous call of the
 * same thread.
 *
 * Samples: one slot per thread remembers the last sampler (weakly), quart and result; an identical next call returns
 * the remembered TargetPoint, an immutable record, instead of an equal new one. A sample is a function of the sampler's
 * six density functions at one position, which holds for every vanilla density function type; a sampler is audited once
 * (its graphs must contain only {@link DensityAudit}'s known pure types) before its first repeat is served, and a refused
 * sampler always computes.
 *
 * Searches: one slot per thread remembers the last tree and metric (weakly), target and result. RTree.search starts from
 * the leaf this thread found last (a thread-local that seeds tie-breaking) and stores the new leaf there. Right after a
 * search for target t that leaf is t's answer, and searching t again from it returns that leaf again (a node replaces the
 * best leaf only when strictly closer) and stores it again. So a repeat of the same tree, metric and an equal target
 * returns the remembered value and leaves the thread-local exactly as the search would. Any other search of the thread
 * replaces the slot, so a remembered value always belongs to the thread's latest search.
 */
public final class ClimateRepeat {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Runtime switches (the config switches act when classes are transformed); -Dbons_and_furious.climateSampleRepeat=false etc. also turn them off. */
    public static volatile boolean sampleEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.climateSampleRepeat", "true"));
    public static volatile boolean searchEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.climateSearchRepeat", "true"));
    static final boolean METRICS = Boolean.getBoolean("bons_and_furious.climateRepeat.metrics");
    /** Served repeats (only with -Dbons_and_furious.climateRepeat.metrics=true). */
    public static final LongAdder SAMPLE_REPEATS = new LongAdder(), SEARCH_REPEATS = new LongAdder();
    /** Returned by {@link #lastSearch} when there is nothing to reuse (a search result may legitimately be null). */
    public static final Object NONE = new Object();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /** Implemented by Climate.Sampler through the mixin: 0 = not audited yet, 1 = pure types only, 2 = refused. */
    public interface AuditedSampler {
        byte bons$auditState();

        void bons$auditState(byte state);
    }

    private static final class SampleSlot {
        WeakReference<Object> sampler;
        int x, y, z;
        Object point;
    }

    private static final class SearchSlot {
        WeakReference<Object> tree, metric;
        Object target, value;
        /** The search in progress on this thread (set by a miss at its start, consumed at its return). */
        Object pendingTree, pendingTarget, pendingMetric;
    }

    private static final ThreadLocal<SampleSlot> SAMPLE = ThreadLocal.withInitial(SampleSlot::new);
    private static final ThreadLocal<SearchSlot> SEARCH = ThreadLocal.withInitial(SearchSlot::new);

    private ClimateRepeat() {
    }

    /** Start of Climate.Sampler.sample: this thread's previous result for the same sampler and quart, or null. */
    public static Object lastSample(Object sampler, int x, int y, int z) {
        if (!sampleEnabled) return null;
        SampleSlot s = SAMPLE.get();
        WeakReference<Object> ref = s.sampler;
        if (ref == null || s.x != x || s.y != y || s.z != z || ref.get() != sampler || !audited(sampler)) return null;
        if (METRICS) SAMPLE_REPEATS.increment();
        return s.point;
    }

    /** Return of Climate.Sampler.sample: remember the result and return it unchanged. */
    public static Object rememberSample(Object sampler, int x, int y, int z, Object point) {
        if (!sampleEnabled) return point;
        SampleSlot s = SAMPLE.get();
        WeakReference<Object> ref = s.sampler;
        if (ref == null || ref.get() != sampler) s.sampler = new WeakReference<>(sampler);
        s.x = x;
        s.y = y;
        s.z = z;
        s.point = point;
        return point;
    }

    /**
     * Start of Climate.RTree.search: the value of this thread's previous search when it was the same query, else
     * {@link #NONE} (and the query is noted so {@link #rememberSearch} can complete it).
     */
    public static Object lastSearch(Object tree, Object target, Object metric) {
        if (!searchEnabled) return NONE;
        SearchSlot s = SEARCH.get();
        WeakReference<Object> t = s.tree, m = s.metric;
        if (t != null && t.get() == tree && m.get() == metric && target != null && target.equals(s.target)) {
            s.pendingTree = null;
            if (METRICS) SEARCH_REPEATS.increment();
            return s.value;
        }
        s.pendingTree = tree;
        s.pendingTarget = target;
        s.pendingMetric = metric;
        return NONE;
    }

    /** Return of Climate.RTree.search: remember the noted query with its value, return the value unchanged. */
    public static Object rememberSearch(Object tree, Object value) {
        if (!searchEnabled) return value;
        SearchSlot s = SEARCH.get();
        if (s.pendingTree != tree) return value;            // a served repeat (nothing noted), or not this search
        if (s.tree == null || s.tree.get() != tree) s.tree = new WeakReference<>(tree);
        if (s.metric == null || s.metric.get() != s.pendingMetric) s.metric = new WeakReference<>(s.pendingMetric);
        s.target = s.pendingTarget;
        s.value = value;
        s.pendingTree = s.pendingTarget = s.pendingMetric = null;
        return value;
    }

    /** True once the sampler's six density functions are known to be pure types (audited on first use, then cached). */
    static boolean audited(Object sampler) {
        if (!(sampler instanceof AuditedSampler a)) return false;
        byte state = a.bons$auditState();
        if (state == 0) {
            String refusal;
            try {
                List<Object> functions = new ArrayList<>(6);
                for (Field f : DensityAudit.fields(sampler.getClass())) {
                    Object v = f.get(sampler);
                    if (v instanceof DensityFunction) functions.add(v);
                }
                refusal = functions.size() == 6 ? DensityAudit.refusal(functions.toArray()) : "unexpected Climate.Sampler layout";
            } catch (Throwable t) {
                refusal = "audit failed: " + t;
            }
            state = refusal == null ? (byte) 1 : (byte) 2;
            a.bons$auditState(state);
            if (refusal != null && LOGGED.add(refusal))
                LOGGER.info("Bons and Furious: vanilla_climate_sample_repeat keeps the original code for climate samplers with {}", refusal);
        }
        return state == 1;
    }
}
