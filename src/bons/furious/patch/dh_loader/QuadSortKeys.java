package bons.furious.patch.dh_loader;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.BufferQuad;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch distanthorizons_quad_sort_keys (Distant Horizons 3.3.2, LGPL-3.0; client). No Distant Horizons
 * code here.
 *
 * Before merging neighbouring LOD quads, LodQuadBuilder.mergeQuadsInternal sorts each direction's quad list with
 * ArrayList.sort and the comparator q1.compare(q2, mergeDirection) = Long.compare of the two quads' precomputed sort keys
 * (sortKeyEastWest or sortKeyNorthSouth, chosen by mergeDirection), after checking that both quads face the same
 * direction. ArrayList.sort is a stable TimSort over the BufferQuad objects: every comparison loads two quads.
 *
 * DH computes the keys as (x << 48 | y << 32 | z << 16) over shorts (BufferQuad.computeSortKey), so their low 16 bits are
 * always zero. For a list of at most 65,536 quads, (key | index in the list) is therefore unique, orders exactly like the
 * key, and breaks ties by the original position: sorted as a primitive long[] it yields the permutation of a stable sort
 * by key, i.e. TimSort's result. The quads are written back in that order.
 *
 * Which key: the comparator (a lambda that captured mergeDirection) is asked once per list to compare two probe quads of
 * one direction whose east-west keys and north-south keys are in opposite orders (BufferQuad.compare only reads the two
 * quads' fields), so its sign names the key it compares. The original call (DH's sort with DH's comparator, so with its
 * exceptions) runs instead whenever a premise does not hold: the switch off, fewer than 2 or more than 65,536 quads, a
 * null element, a quad facing another direction than the first (DH's comparator throws for that pair), a key whose low 16
 * bits are not zero, or a comparator whose probe answer is 0. Scratch arrays are per thread (DH builds on several loader
 * threads) and keep no quad.
 */
public final class QuadSortKeys {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.dhQuadSortKeys=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhQuadSortKeys", "true"));
    /** Shadow mode for rigs: every list is also sorted with DH's comparator (on a copy) and the orders compared (WARN on a difference; DH's order is kept). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.dhQuadSortKeys.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final int MAX_QUADS = 1 << 16;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    /** Probe quads: east-west keys ascending A < B, north-south keys descending A > B. Never handed to DH's lists. */
    private static final BufferQuad PROBE_A = probe(1L << 16, 2L << 16), PROBE_B = probe(2L << 16, 1L << 16);
    private static volatile boolean announced;

    private static final class Scratch {
        long[] keys = new long[1024];
        BufferQuad[] quads = new BufferQuad[1024];

        void ensure(int n) {
            if (keys.length < n) {
                int c = Math.max(n, keys.length * 2);
                keys = new long[c];
                quads = new BufferQuad[c];
            }
        }
    }

    private QuadSortKeys() {
    }

    private static BufferQuad probe(long eastWest, long northSouth) {
        BufferQuad q = new BufferQuad();
        q.direction = EDhDirection.UP;
        q.sortKeyEastWest = eastWest;
        q.sortKeyNorthSouth = northSouth;
        return q;
    }

    /** In place of list.sort(comparator) in mergeQuadsInternal; {@code comparator} is DH's own (the original call uses it). */
    public static void sort(ArrayList<BufferQuad> list, Comparator<? super BufferQuad> comparator) {
        int n = list.size();
        if (!enabled || n < 2 || n > MAX_QUADS) {
            list.sort(comparator);
            return;
        }
        BufferQuad first = list.get(0);
        int probe = comparator.compare(PROBE_A, PROBE_B);
        if (first == null || probe == 0) {
            list.sort(comparator);
            return;
        }
        boolean eastWest = probe < 0;
        EDhDirection direction = first.direction;
        Scratch s = SCRATCH.get();
        s.ensure(n);
        long[] keys = s.keys;
        BufferQuad[] quads = s.quads;
        for (int i = 0; i < n; i++) {
            BufferQuad q = list.get(i);
            long key;
            if (q == null || q.direction != direction || ((key = eastWest ? q.sortKeyEastWest : q.sortKeyNorthSouth) & 0xFFFFL) != 0L) {
                Arrays.fill(quads, 0, i, null);
                list.sort(comparator);
                return;
            }
            keys[i] = key | i;
            quads[i] = q;
        }
        ArrayList<BufferQuad> expected = SHADOW ? shadowSorted(list, comparator) : null;
        Arrays.sort(keys, 0, n);
        for (int i = 0; i < n; i++) list.set(i, quads[(int) (keys[i] & 0xFFFFL)]);
        Arrays.fill(quads, 0, n, null);
        if (expected != null && !shadowSame(list, expected)) {
            for (int i = 0; i < n; i++) list.set(i, expected.get(i));    // a failed check keeps DH's order
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_quad_sort_keys sorts Distant Horizons' LOD quads by their keys as primitive values");
        }
    }

    private static ArrayList<BufferQuad> shadowSorted(ArrayList<BufferQuad> list, Comparator<? super BufferQuad> comparator) {
        ArrayList<BufferQuad> copy = new ArrayList<>(list);
        copy.sort(comparator);
        return copy;
    }

    private static boolean shadowSame(ArrayList<BufferQuad> sorted, ArrayList<BufferQuad> expected) {
        SHADOW_CHECKS.incrementAndGet();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) != expected.get(i)) {
                if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: distanthorizons_quad_sort_keys shadow check: order differs from DH's sort at index {} of {}", i, sorted.size());
                return false;
            }
        }
        return true;
    }
}
