package bons.furious.patch.modernfix_bake;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch modernfix_bake_location_order (ModernFix 5.27.77+mc1.20.1, LGPL-3.0; tested build
 * modernfix-forge-5.27.77+mc1.20.1; client only): flag, shadow mode, counters and the start-up self-test of
 * {@link LocationOrderSet}. No ModernFix code.
 *
 * <p>ModelBakeEventHelper's constructor (ModernFix's emulated ModelEvent.ModifyBakingResult, run on every resource reload)
 * creates its top-level model-location set with {@code new ObjectLinkedOpenHashSet(blockStates + items)}; with the switch
 * on, that expression yields a {@link LocationOrderSet} of the same expected size instead (LocationOrderMixin). Its
 * contents, order, lookups and every method of fastutil's class are unchanged; only iteration reads an insertion-order
 * array. Why the order is the same and when the array is not used: LocationOrderSet.
 *
 * <p>Library guard: fastutil (8.5.9 in every 1.20.1 install) is a library, which fingerprint guards cannot cover, so the
 * contract the array relies on (add inserts at the end of the link chain and returns true exactly when it inserted;
 * addAll inserts through add) is checked once at start-up on a sample: if the array and the superclass's own iteration
 * ever differ there, the switch turns itself off (WARN). In addition every iteration checks that the array has size()
 * entries.
 */
public final class BakeLocations {
    /** Runtime switch, read when ModernFix builds the set (each resource reload) and on every iteration. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modernfixBakeLocationOrder", "true"));
    /** Shadow mode for rigs: every iteration also walks the link chain, compares, and the game gets the link-chain iterator. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.modernfixBakeLocationOrder.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Sets built, iterations served from the array, iterator()/forEach calls that used the link chain instead. */
    public static final AtomicLong SETS = new AtomicLong(), ITERATIONS = new AtomicLong(), FALLBACKS = new AtomicLong();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    private static volatile Boolean selfTest;

    private BakeLocations() {
    }

    /** The switch decides at set creation: on and the library behaves as the array assumes. */
    public static boolean active() {
        if (!enabled) return false;
        Boolean t = selfTest;
        if (t == null) {
            t = selfTest();
            selfTest = t;
            if (!t) LOGGER.warn("Bons and Furious: modernfix_bake_location_order stays off: fastutil's ObjectLinkedOpenHashSet does not iterate in add order here");
        }
        return t;
    }

    static boolean selfTest() {
        try {
            LocationOrderSet ours = new LocationOrderSet(8);
            SETS.decrementAndGet();   // the sample does not count as one of ModernFix's sets
            ObjectLinkedOpenHashSet<ResourceLocation> plain = new ObjectLinkedOpenHashSet<>(8);
            List<ResourceLocation> seq = new ArrayList<>();
            for (int i = 0; i < 200; i++) seq.add(new ResourceLocation("bons_selftest", "p" + (i * 7919 % 97)));   // repeats
            for (ResourceLocation r : seq) if (ours.add(r) != plain.add(r)) return false;
            HashSet<ResourceLocation> more = new HashSet<>();
            for (int i = 0; i < 300; i++) more.add(new ResourceLocation("bons_selftest", "q" + (i % 150)));
            more.add(seq.get(3));
            if (ours.addAll(more) != plain.addAll(more)) return false;   // also grows past the expected size: rehash keeps order
            if (!ours.inStep() || ours.size() != plain.size()) return false;
            ObjectListIterator<ResourceLocation> a = ours.arrayIterator(), b = plain.iterator();
            while (a.hasNext()) if (!b.hasNext() || a.next() != b.next()) return false;
            return !b.hasNext();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: modernfix_bake_location_order walks ModernFix's model-location set from its insertion-order array");
        }
    }

    /** Shadow mode: the array against the link chain, element by element (identity), and their lengths. */
    static void shadow(LocationOrderSet set, Object[] order, int count) {
        SHADOW_CHECKS.incrementAndGet();
        ObjectListIterator<ResourceLocation> it = set.linkIterator();
        int i = 0;
        boolean same = true;
        while (it.hasNext()) {
            ResourceLocation k = it.next();
            if (i >= count || order[i] != k) {
                same = false;
                break;
            }
            i++;
        }
        if (same && i != count) same = false;
        if (!same) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: modernfix_bake_location_order shadow mismatch at element {} of {} (set size {})", i, count, set.size());
        }
    }
}
