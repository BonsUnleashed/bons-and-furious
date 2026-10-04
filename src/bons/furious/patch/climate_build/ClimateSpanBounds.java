package bons.furious.patch.climate_build;

import bons.furious.mixin.climate_build.ClimateNodeSpanAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.biome.Climate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_climate_tree_span_bounds (Minecraft 1.20.1, both sides). No Minecraft code here.
 *
 * Every climate search tree subtree computes its parameter space with Climate.RTree.buildParameterSpace(children): for
 * each child and each of the seven dimensions it widens the running range with Parameter.span, which allocates a new
 * Parameter every time (7 x children objects per subtree, and the tree build makes subtrees for all nodes eight times
 * per level while it tries each split dimension: about two million short-lived objects for one overworld tree).
 *
 * The same ranges are computed here directly: per dimension the smallest min and the largest max over the children, in
 * the same order with the same comparisons (min/max of longs), one Parameter per dimension at the end. Identity follows
 * the original: with one child the original returns that child's own Parameter objects (span(null) returns itself), so
 * this does too; with more children the original's results are new objects, and so are these. The result is a new
 * java.util.ArrayList of seven entries, as the original's. An empty list (the original throws), a null child, a space
 * shorter than seven or a null Parameter take the original, as does any list that is not random-access.
 */
public final class ClimateSpanBounds {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.vanillaClimateTreeSpanBounds=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaClimateTreeSpanBounds", "true"));
    /** Counters (read by probes): parameter spaces computed here, spaces left to the original. */
    public static final AtomicLong SPANS = new AtomicLong(), FALLBACKS = new AtomicLong();

    private static final int DIMS = 7;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private ClimateSpanBounds() {
    }

    /** buildParameterSpace(children), or null when the caller must run the original (nothing has been changed then). */
    public static List<Climate.Parameter> span(List<?> children) {
        int n = children.size();
        if (n == 0 || !(children instanceof RandomAccess)) return fallback();
        long[] min = new long[DIMS], max = new long[DIMS];
        Climate.Parameter[] only = null;
        for (int i = 0; i < n; i++) {
            if (!(children.get(i) instanceof ClimateNodeSpanAccessor a)) return fallback();
            Climate.Parameter[] space = a.bons$spanSpace();
            if (space == null || space.length < DIMS) return fallback();
            for (int d = 0; d < DIMS; d++) {
                Climate.Parameter p = space[d];
                if (p == null) return fallback();
                long lo = p.f_186813_(), hi = p.f_186814_();
                if (i == 0) {
                    min[d] = lo;
                    max[d] = hi;
                } else {
                    min[d] = Math.min(lo, min[d]);
                    max[d] = Math.max(hi, max[d]);
                }
            }
            if (i == 0) only = space;
        }
        ArrayList<Climate.Parameter> out = new ArrayList<>();
        for (int d = 0; d < DIMS; d++) out.add(n == 1 ? only[d] : new Climate.Parameter(min[d], max[d]));
        SPANS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_climate_tree_span_bounds computes climate subtree ranges without intermediate objects");
        }
        return out;
    }

    private static List<Climate.Parameter> fallback() {
        FALLBACKS.incrementAndGet();
        return null;
    }
}
