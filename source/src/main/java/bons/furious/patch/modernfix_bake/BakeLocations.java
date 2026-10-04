package bons.furious.patch.modernfix_bake;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch modernfix_bake_location_order (ModernFix, LGPL-3.0; 1.21.1 tested build
 * modernfix-neoforge-5.27.24+mc1.21.1; client only): flag, shadow mode, counters and the start-up self-test of
 * {@link LocationOrderSet}. No ModernFix code.
 *
 * <p>ModelBakeEventHelper's constructor (ModernFix's emulated ModelEvent.ModifyBakingResult and BakingCompleted, run on
 * every resource reload when ModernFix's dynamic resources are on) creates its top-level model-location set with
 * {@code new ObjectLinkedOpenHashSet(blockStates + items)}; with the switch on, that expression yields a
 * {@link LocationOrderSet} of the same expected size instead (LocationOrderMixin). Its contents, order, lookups and every
 * method of fastutil's class are unchanged; only iteration reads an insertion-order array. Why the order is the same and
 * when the array is not used: LocationOrderSet.
 *
 * <p>Library guard: fastutil (8.5.12 in Minecraft 1.21.1) is a library, which fingerprint guards cannot cover, so the
 * contract the array relies on (add inserts at the end of the link chain and returns true exactly when it inserted;
 * addAll inserts through add) is checked once at start-up on a sample: if the array and the superclass's own iteration
 * ever differ there, the switch turns itself off (WARN). In addition every iteration checks that the array has size()
 * entries.
 *
 * <p>Ported to 1.21.1: the set's elements are ModelResourceLocation records (1.21 split them from ResourceLocation), so
 * the self-test samples are ModelResourceLocations built with ResourceLocation.fromNamespaceAndPath (the ResourceLocation
 * constructor is private in 1.21.1). fastutil 8.5.12's ObjectLinkedOpenHashSet differs from 8.5.9 only in a public
 * ensureCapacity (a resize: the link order is kept) and in removeFirst/removeLast resetting first/last on the last removal
 * (both already mark this set diverged); add, addAll, addOrGet, iterator and forEach are byte-identical.
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

    private static ModelResourceLocation sample(String path, String variant) {
        return new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("bons_selftest", path), variant);
    }

    static boolean selfTest() {
        try {
            LocationOrderSet ours = new LocationOrderSet(8);
            SETS.decrementAndGet();   // the sample does not count as one of ModernFix's sets
            ObjectLinkedOpenHashSet<ModelResourceLocation> plain = new ObjectLinkedOpenHashSet<>(8);
            List<ModelResourceLocation> seq = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                int p = i * 7919 % 97;
                seq.add(sample("p" + p, (p & 1) == 0 ? "inventory" : "facing=north"));   // repeats (equal, not identical, keys)
            }
            for (ModelResourceLocation r : seq) if (ours.add(r) != plain.add(r)) return false;
            HashSet<ModelResourceLocation> more = new HashSet<>();
            for (int i = 0; i < 300; i++) more.add(sample("q" + (i % 150), "inventory"));
            more.add(seq.get(3));
            if (ours.addAll(more) != plain.addAll(more)) return false;   // also grows past the expected size: rehash keeps order
            if (!ours.inStep() || ours.size() != plain.size()) return false;
            ObjectListIterator<ModelResourceLocation> a = ours.arrayIterator(), b = plain.iterator();
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
        ObjectListIterator<ModelResourceLocation> it = set.linkIterator();
        int i = 0;
        boolean same = true;
        while (it.hasNext()) {
            ModelResourceLocation k = it.next();
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
