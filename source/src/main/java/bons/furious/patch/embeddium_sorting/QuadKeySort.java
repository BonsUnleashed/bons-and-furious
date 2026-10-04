package bons.furious.patch.embeddium_sorting;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch embeddium_entity_sort_radix (tested build: Embeddium 1.0.15+mc1.21.1 on Minecraft 1.21.1 /
 * NeoForge 21.1.252, client only; first written for Embeddium 0.3.31+mc1.20.1).
 * Helper of bons.furious.mixin.embeddium_sorting.VertexSorterRadixMixin.
 *
 * Ported to 1.21.1: nothing in this helper changed; Embeddium 1.0.15's MergeSort / InsertionSort are the 0.3.31 code
 * (identical decompiles after the package rename), so the order to reproduce is the same.
 *
 * Embeddium overwrites VertexSorting.byDistance(x, y, z), so every translucent quad buffer that is sorted on upload
 * (entity_translucent, item_entity_translucent_cull, translucent, beacon beams, ...; with Oculus each batched segment)
 * ends in Embeddium's MergeSort.mergeSort(float[] keys): the keys are the squared distances of the quad centres, and
 * the result is the quad order, far quads first. That sort is a top-down merge sort over an index array with an
 * insertion sort below 16 elements. Its comparisons are keys[u] < keys[t] (insertion) and keys[q] <= keys[p] (merge,
 * plus the already-ordered shortcut keys[mid] <= keys[mid - 1]): for keys without NaN this is exactly the STABLE sort by
 * descending numeric key. Equal keys keep their input order and -0.0 counts as equal to +0.0; the result is therefore
 * the unique permutation "descending key, ties by ascending index".
 *
 * sort() produces that same permutation another way. Each key becomes a 32-bit pattern d whose unsigned order is the
 * DESCENDING numeric order (floats mapped to sortable ints, -0.0 folded onto +0.0, then complemented), and d is packed
 * with the index into one long (d in the high half, the index in the low half). Ordering the longs by (d, index) is the
 * required permutation, and because every packed long is distinct, any correct sort of them gives that one order:
 * Arrays.sort for short arrays, a stable least-significant-digit radix sort on d (8-bit digits, digits shared by every
 * key are skipped) from RADIX_MIN up. A key that is NaN (only for broken quad positions) makes Embeddium's comparisons
 * inconsistent, so sort() returns null and the caller runs Embeddium's own merge sort: same result in every case.
 * Arrays shorter than MIN_LENGTH also stay with Embeddium (no gain there). The packed longs and digit counts live in
 * per-thread scratch arrays (the sort calls nothing outside this class, so it cannot re-enter itself), and only the
 * returned int[] is new, as in Embeddium. The keys array is only read, as in Embeddium. No Embeddium code is carried.
 */
public final class QuadKeySort {
    /** Runtime switch; -Dbons_and_furious.entityQuadRadixSort=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.entityQuadRadixSort", "true"));
    /**
     * Rig self-check: -Dbons_and_furious.entityQuadRadixSort.shadow=true keeps Embeddium's result for every sort and only
     * compares it with sort()'s.
     */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.entityQuadRadixSort.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Sorts handled here and sorts left to Embeddium (NaN key), for the rig's probe. */
    public static final AtomicLong SORTS = new AtomicLong(), FALLBACKS = new AtomicLong();
    /**
     * Shorter arrays are left to Embeddium: below about 48 keys its insertion sort and short merges are as fast as either
     * packed sort, and the radix sort's lead is still small and noisy from 48 to 63 keys (1.0-1.36x through
     * VertexSorters, 31 interleaved rounds); from 64 keys it is 1.35-1.74x, from 96 keys 1.5-1.8x and more.
     */
    public static final int MIN_LENGTH = Integer.getInteger("bons_and_furious.entityQuadRadixSort.minLength", 64);
    /** From this length on the radix sort is used (below it Arrays.sort on the packed longs; unused at the defaults). */
    public static final int RADIX_MIN = Integer.getInteger("bons_and_furious.entityQuadRadixSort.radixMin", 64);
    /** SHADOW mode only: sort calls by length, bucket b = lengths in [2^b, 2^(b+1)) (bucket 0 also holds 0). */
    public static final java.util.concurrent.atomic.AtomicLongArray LENGTHS = new java.util.concurrent.atomic.AtomicLongArray(32);
    /** Per-thread scratch arrays are kept up to this length (longer sorts allocate their own). */
    private static final int SCRATCH_MAX = 1 << 16;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private QuadKeySort() {
    }

    /** Reused packed-key arrays and digit counts of one thread (the sort calls nothing outside this class). */
    private static final class Scratch {
        long[] a = new long[0], b = new long[0];
        final int[] counts = new int[4 * 256];
    }

    /**
     * The quad order Embeddium's MergeSort.mergeSort(keys) returns (indices, far quads first, ties in input order), or
     * null when a key is NaN (then the caller runs Embeddium's sort). Arrays shorter than MIN_LENGTH are also left to the
     * caller.
     */
    public static int[] sort(float[] keys) {
        int n = keys.length;
        if (n < MIN_LENGTH || n < 2) return null;
        Scratch s = n <= SCRATCH_MAX ? SCRATCH.get() : new Scratch();
        long[] a = s.a;
        if (a.length < n) s.a = a = new long[Math.max(n, Math.min(SCRATCH_MAX, a.length * 2))];
        for (int i = 0; i < n; i++) {
            float k = keys[i];
            if (k != k) {
                FALLBACKS.incrementAndGet();
                return null;
            }
            a[i] = ((long) descendingKey(k) << 32) | i;
        }
        if (n < RADIX_MIN) {
            // signed long order of (d ^ 0x80000000, index) == unsigned order of (d, index)
            for (int i = 0; i < n; i++) a[i] ^= Long.MIN_VALUE;
            Arrays.sort(a, 0, n);
        } else {
            if (s.b.length < a.length) s.b = new long[a.length];
            a = radix(a, s.b, s.counts, n);
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = (int) a[i];
        SORTS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: embeddium_entity_sort_radix: translucent quads are ordered by a packed-key sort with Embeddium's "
                    + "exact order{}", SHADOW ? " (SHADOW mode: comparing only)" : "");
        }
        return out;
    }

    /**
     * 32-bit pattern whose unsigned order is the descending numeric order of non-NaN floats; -0.0 and +0.0 map to the
     * same pattern (Embeddium's comparisons treat them as equal).
     */
    static int descendingKey(float k) {
        int b = k == 0.0f ? 0 : Float.floatToRawIntBits(k);
        return ~(b ^ ((b >> 31) | 0x80000000));
    }

    /**
     * Stable LSD radix sort of a[0..n) by the high 32 bits (unsigned), 8 bits per pass, b as the other buffer; returns
     * the array (a or b) that holds the sorted run.
     */
    private static long[] radix(long[] a, long[] b, int[] counts, int n) {
        Arrays.fill(counts, 0);
        for (int i = 0; i < n; i++) {
            int d = (int) (a[i] >>> 32);
            counts[d & 0xFF]++;
            counts[256 + ((d >>> 8) & 0xFF)]++;
            counts[512 + ((d >>> 16) & 0xFF)]++;
            counts[768 + (d >>> 24)]++;
        }
        for (int pass = 0; pass < 4; pass++) {
            int base = pass * 256;
            int shift = 32 + pass * 8;
            // a digit that every key shares leaves the order unchanged: skip the pass
            int first = (int) ((a[0] >>> shift) & 0xFF);
            if (counts[base + first] == n) continue;
            int sum = 0;
            for (int i = base; i < base + 256; i++) {
                int t = counts[i];
                counts[i] = sum;
                sum += t;
            }
            for (int i = 0; i < n; i++) {
                long v = a[i];
                b[counts[base + (int) ((v >>> shift) & 0xFF)]++] = v;
            }
            long[] t = a;
            a = b;
            b = t;
        }
        return a;
    }

    /** SHADOW mode: compare sort(keys) with Embeddium's result (which the caller returns); records the length. */
    public static int[] shadow(float[] keys, int[] embeddium) {
        LENGTHS.incrementAndGet(31 - Integer.numberOfLeadingZeros(Math.max(1, keys.length)));
        int[] ours = sort(keys);
        if (ours != null) {
            SHADOW_CHECKS.incrementAndGet();
            if (!Arrays.equals(ours, embeddium) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: embeddium_entity_sort_radix SHADOW mismatch for {} keys (first differing index {})",
                        keys.length, Arrays.mismatch(ours, embeddium));
            }
        }
        return embeddium;
    }
}
