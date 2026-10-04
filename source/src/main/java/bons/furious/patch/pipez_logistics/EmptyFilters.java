package bons.furious.patch.pipez_logistics;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch pipez_empty_filter_fast_path (Pipez, All Rights Reserved: no Pipez code is carried; 1.21.1 tested
 * build: pipez-neoforge-1.21.1-1.2.31; server side, incl. the integrated server). Not in our pack: public value.
 *
 * What Pipez does. ItemPipeType and FluidPipeType ask canInsert(provider, connection, stack, filters) for every source slot
 * or tank they try for every destination, every transfer (round robin: for every single item). canInsert first collects
 * the inverted filters that match the connection into a new list through a stream and refuses on a match, then collects
 * the normal filters the same way: no normal filter for this connection means "allowed", else one must match. Without any
 * filter configured (the default; filters only exist on a configured upgrade) both streams run over the empty list and
 * the answer is "allowed".
 *
 * What the switch does. With an empty filter list canInsert answers true at once. That is the original's answer: over an
 * empty list neither stream calls anything (no matchesConnection, no matches, so the registry provider is not used either),
 * the first loop has nothing to refuse and the second list is empty. Any non-empty list, and a null one (the original's own
 * NullPointerException), run the original.
 *
 * Ported to 1.21.1: Pipez 1.2.31's ItemPipeType / FluidPipeType.canInsert take a HolderLookup.Provider first (passed only
 * to matches, which an empty list never reaches) and a List of Filter&lt;?, ?&gt;; the bodies are otherwise the 1.20.1 ones.
 * GasPipeType (Mekanism chemicals) is left to the original on 1.21.1: its canInsert is unchanged, but Mekanism's API (an
 * optional Pipez dependency) is not among the pinned 1.21.1 targets, so that mixin can be neither compiled nor audited.
 *
 * -Dbons_and_furious.pipezEmptyFilterFastPath=false switches it off at run time.
 * -Dbons_and_furious.pipezEmptyFilterFastPath.shadow=true (verification runs only): the original runs as a nested call and
 * must answer true; SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN.
 */
public final class EmptyFilters {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.pipezEmptyFilterFastPath", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.pipezEmptyFilterFastPath.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private EmptyFilters() {
    }

    /** True when canInsert can answer true without running: the switch is on and the filter list is empty. */
    public static boolean unfiltered(List<?> filters) {
        if (!enabled || filters == null || !filters.isEmpty()) {
            return false;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: pipez_empty_filter_fast_path applies (pipes without filters no longer build filter streams per slot){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return true;
    }

    /** Shadow mode: what the original answered for an empty filter list. */
    public static void check(boolean original, String type) {
        SHADOW_CHECKS.incrementAndGet();
        if (!original) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) {
                LOGGER.warn("Bons and Furious: pipez_empty_filter_fast_path shadow mismatch #{}: {}.canInsert refused with no filter", m, type);
            }
        }
    }
}
