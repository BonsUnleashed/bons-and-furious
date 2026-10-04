package bons.furious.patch.alexsmobs_search;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch alexsmobs_partner_min_scan (Alex's Mobs 1.22.9, LGPL-3.0; both sides, acts where mob AI runs).
 *
 * Three Alex's Mobs goals pick the nearest candidate by sorting the whole candidate list and taking its first element:
 * the Devil's Hole pupfish chase-partner search (EntityDevilsHolePupfish$ChaseGoal.canUse), the triops mate search
 * (EntityTriops$BreedGoal.canUse) and the catfish food search (EntityCatfish$TargetFoodGoal.canUse), each
 * `list.sort(Comparator.comparingDouble(mob::distanceToSqr))` followed only by `list.isEmpty()` / `list.get(0)`.
 *
 * The switch (PartnerSortMixin wraps that List.sort call) moves the first element with the smallest key to the front in one
 * pass with the same comparator instead: List.sort is stable, so the sorted list's first element is the earliest element
 * (in list order) that no other element compares below, and a scan that replaces its pick only on a strictly smaller
 * comparison finds exactly that element. comparingDouble compares with Double.compare (a total order, ties and NaN
 * included) and distanceToSqr is a plain read of positions that nothing moves during the call. The rest of the list keeps
 * another order, which nothing reads: the list is a local of canUse, used afterwards only for isEmpty() and get(0).
 * Lists other than java.util.ArrayList (what Level.getEntitiesOfClass returns) keep the sort.
 */
public final class PartnerScan {
    /** Runtime switch. -Dbons_and_furious.alexsmobsPartnerMinScan=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.alexsmobsPartnerMinScan", "true"));
    /** Probe counters (plain longs, approximate under threads): searches answered by the scan, lists left to the sort. */
    public static long scans, sorts;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private PartnerScan() {
    }

    /** list.sort(cmp) where only the first element is read afterwards. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void sortHead(List list, Comparator cmp, Operation<Void> original) {
        if (!enabled || list == null || list.getClass() != ArrayList.class) {
            sorts++;
            original.call(list, cmp);
            return;
        }
        scans++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: alexsmobs_partner_min_scan picks Alex's Mobs' nearest partner without sorting the candidates");
        }
        int n = list.size();
        if (n < 2) return;
        int best = 0;
        Object head = list.get(0);
        for (int i = 1; i < n; i++) {
            Object e = list.get(i);
            if (cmp.compare(e, head) < 0) {
                best = i;
                head = e;
            }
        }
        if (best != 0) {
            list.set(best, list.get(0));
            list.set(0, head);
        }
    }
}
