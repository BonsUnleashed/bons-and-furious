package bons.furious.patch.jei_search;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch jei_baked_index_background_grams (JEI 19.51.0.418 for Minecraft 1.21.1 / NeoForge, tested build
 * jei-1.21.1-neoforge-19.51.0.418.jar, MIT; client). No JEI code here.
 *
 * JEI builds its search index once per world join, inside the join freeze ("Building ingredient filter", 2.6-4.3 s on
 * the 1.20.1 pack's client, 9.0 s in our test rig with JFR): ElementSearch walks every ingredient once per search
 * prefix, asks the prefix for the ingredient's strings (names, mod names, tags, tooltip words; the tooltip words call
 * into every mod's tooltip code and stay on the render thread) and puts each string into the prefix's
 * BakedSubstringIndex.Builder (net.mezzdev.bakedsubstring, shaded into JEI). Builder.put only appends the key and the
 * value to two lists; build() then does all the work in one go on the render thread: for every key, every 1-, 2- and
 * 3-character gram (de-duplicated within the key through a whole-index "last entry per gram" map) appends the key's
 * index to the gram's posting list, and finally copies the lists into a gram -> int[] map. In our own recording that
 * build is 18.9% of the filter window (the tooltip prefix: roughly one entry per tooltip word of every ingredient).
 *
 * This class lets a builder that reaches START entries hand its keys, as they are put, to one background thread that
 * computes the same postings while the render thread goes on producing the next strings; build() then only waits for
 * that thread (normally finished, because the render thread's tooltip calls are much slower than the gram work) and runs
 * its own code with the background gram map (see BakedIndexBuilderMixin). The background thread only reads the key
 * Strings it was handed (immutable, passed through a queue, so safely published); it never touches the builder, the
 * values or any mod code. Exactness:
 *
 *  - keys and values arrays, the deduplicateResults flag and the constructor call stay build()'s own code on the calling
 *    thread (the repeated-value scan stops adding to its local identity set after the first repeat: the flag is then
 *    already true and the rest of the scan cannot change it);
 *  - postings: the background thread visits every key's grams in the original's order (gram length 1..3, position
 *    0..length-gram) with the original's encoding ((length << 48) | char0 << 32 | char1 << 16 | char2) and appends the
 *    key's index once per distinct gram of the key, in key order, so every gram's array lists exactly the indices the
 *    original's list holds, ascending; arrays are fresh and exactly sized, as IntArrayList.toIntArray returns them;
 *  - map layout: the distinct grams are put into a default-sized Long2ObjectOpenHashMap in the order of their first
 *    occurrence over all keys, which is exactly the order in which the original's mutable map receives its puts (it
 *    only puts at a gram's first occurrence; gets do not move entries), so that map has the original's internal layout,
 *    and the final map is filled from it in its own iteration order with the original's expected size: same layout as
 *    the original's final map, same content.
 *
 * Declines (build() runs unchanged): runtime switch off, a builder that never reached START entries, a put from a second
 * thread, a build on a thread other than the one that put, a key count that does not match what was handed over, a
 * background failure or a background thread that does not finish within WAIT_SECONDS. A builder is handled at most
 * once: after its first build every later put/build is the original's.
 *
 * Ported to 1.21.1: no change. JEI 19.51.0.418's BakedSubstringIndex and Builder decompile identically to 15.59.0.212's
 * (same encodeGram, same addGrams loop and last-entry de-dup, same default-sized mutable map and expected-size final
 * map, same IdentityHashMap-backed repeated-value set). The map-layout argument only needs JEI's build() and this class to
 * use the same fastutil classes in the same JVM; on 1.21.1 that is Minecraft's fastutil 8.5.12 (8.5.9 on 1.20.1).
 */
public final class BackgroundGrams {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.jeiBakedIndexBackgroundGrams=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.jeiBakedIndexBackgroundGrams", "true"));
    /** Shadow mode for rigs: every handled build also runs the original build, compares the two and returns the original's index. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.jeiBakedIndexBackgroundGrams.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters (read by probes): builds answered here, entries indexed in the background, builds left to the original after a start. */
    public static final AtomicLong BUILDS = new AtomicLong(), ENTRIES = new AtomicLong(), FALLBACKS = new AtomicLong();
    /** A builder hands its keys to the background once it holds this many entries (JEI's tooltip and name indexes). */
    public static int START = Integer.getInteger("bons_and_furious.jeiBakedIndexBackgroundGrams.start", 16384);
    /** How long build() waits for the background thread before it builds the original way. */
    public static long WAIT_SECONDS = 120;
    /** A background thread with no keys for this long gives up (the builder was abandoned before build()). */
    static long IDLE_SECONDS = 300;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Object DONE = new Object();
    private static final int BATCH = 1024;
    private static volatile boolean announced;
    private static final AtomicLong WARNINGS = new AtomicLong();

    private BackgroundGrams() {
    }

    /** Implemented on BakedSubstringIndex.Builder by the mixin. */
    public interface Holder {
        List<String> bons$keys();

        Object bons$grams();

        void bons$grams(Object state);
    }

    /** Builder.put(key, value) has just appended key (non-null). */
    public static void afterPut(Holder b, String key) {
        Object s = b.bons$grams();
        if (s == null) {
            List<String> keys = b.bons$keys();
            if (keys.size() < START) return;
            State st = new State(Thread.currentThread());
            b.bons$grams(st);
            st.handAll(keys);
            return;
        }
        if (s instanceof State st) st.add(key);
    }

    /**
     * Builder.build() is starting on a builder that handed its keys over: the background gram map to build with, or null
     * where this declines (build() then runs unchanged). Either way the builder is not handled again.
     */
    public static Long2ObjectOpenHashMap<int[]> prepare(Holder b) {
        Object s = b.bons$grams();
        b.bons$grams(DONE);
        if (!(s instanceof State st)) return null;
        if (!enabled || st.owner != Thread.currentThread() || st.poisoned) {
            st.abandon();
            FALLBACKS.incrementAndGet();
            return null;
        }
        Long2ObjectOpenHashMap<int[]> byGram = st.finish(b.bons$keys().size());
        if (byGram == null) FALLBACKS.incrementAndGet();
        return byGram;
    }

    /** A build with the background gram map has returned (entries = its key count). */
    public static void built(int entries) {
        BUILDS.incrementAndGet();
        ENTRIES.addAndGet(entries);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: jei_baked_index_background_grams computes JEI's search-index grams in the background while the strings are made ({} entries in the first index)", entries);
        }
    }

    /** Shadow mode: compares the original build's index with ours (WARN for at most 20 mismatches). */
    public static void shadow(Object original, Object ours) {
        SHADOW_CHECKS.incrementAndGet();
        String diff = BakedIndexCompare.diff(original, ours);
        if (diff != null) {
            SHADOW_MISMATCHES.incrementAndGet();
            if (WARNINGS.incrementAndGet() <= 20) LOGGER.warn("Bons and Furious: jei_baked_index_background_grams shadow mismatch: {}", diff);
        }
    }

    /** One builder's background indexing. The owner thread puts; the background thread indexes. */
    static final class State implements Runnable {
        final Thread owner;
        volatile boolean poisoned;
        private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        private final CountDownLatch done = new CountDownLatch(1);
        private String[] batch = new String[BATCH];
        private int fill;
        private int handed;
        private volatile boolean abandoned;
        // results, written by the background thread before done.countDown()
        private Long2ObjectOpenHashMap<int[]> result;
        private Throwable failure;
        private int indexed;

        State(Thread owner) {
            this.owner = owner;
        }

        /** Owner thread: the builder just reached START entries; hand over everything put so far. */
        void handAll(List<String> keys) {
            Thread t = new Thread(this, "Bons JEI search index");
            t.setDaemon(true);
            t.start();
            for (int i = 0, n = keys.size(); i < n; i++) add(keys.get(i));
        }

        void add(String key) {
            if (Thread.currentThread() != owner) {
                poisoned = true;
                return;
            }
            if (poisoned) return;
            batch[fill++] = key;
            handed++;
            if (fill == BATCH) {
                queue.add(new Batch(batch, fill));
                batch = new String[BATCH];
                fill = 0;
            }
        }

        /** Owner thread: no more keys; wait for the background postings. Null = build the original way. */
        Long2ObjectOpenHashMap<int[]> finish(int keyCount) {
            if (keyCount != handed) {
                abandon();
                return null;
            }
            queue.add(new Batch(batch, fill));
            queue.add(DONE);
            batch = null;
            boolean interrupted = false;
            try {
                if (!done.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    abandoned = true;
                    LOGGER.warn("Bons and Furious: jei_baked_index_background_grams: the background index did not finish in {} s; building the original way", WAIT_SECONDS);
                    return null;
                }
            } catch (InterruptedException ie) {
                interrupted = true;
            }
            if (interrupted) {
                abandoned = true;
                Thread.currentThread().interrupt();
                return null;
            }
            if (failure != null) {
                LOGGER.warn("Bons and Furious: jei_baked_index_background_grams: the background index failed; building the original way", failure);
                return null;
            }
            return indexed == keyCount ? result : null;
        }

        void abandon() {
            abandoned = true;
            queue.add(DONE);
        }

        @Override
        public void run() {
            GramIndex index = new GramIndex();
            try {
                boolean complete = false;
                while (!abandoned) {
                    Object m = queue.poll(IDLE_SECONDS, TimeUnit.SECONDS);
                    if (m == null) break;              // nobody finished this builder: give up, build() (if ever) declines
                    if (m == DONE) {
                        complete = true;
                        break;
                    }
                    Batch b = (Batch) m;
                    for (int i = 0; i < b.count; i++) index.add(b.keys[i]);
                }
                if (complete && !abandoned) {
                    indexed = index.entries;
                    result = index.assemble();
                } else {
                    indexed = -1;
                }
            } catch (Throwable t) {
                failure = t;
            } finally {
                done.countDown();
            }
        }
    }

    record Batch(String[] keys, int count) {
    }

    /**
     * The postings of a sequence of keys (entry index = position in the sequence), built incrementally. Gram ids are
     * handed out in first-occurrence order; grams of printable ASCII characters get theirs through a flat table indexed
     * by the characters, all others through a small hash map.
     */
    public static final class GramIndex {
        static final int R = 95;                         // printable ASCII 0x20..0x7E
        static final int B2 = R, B3 = R + R * R, TABLE = R + R * R + R * R * R;
        private final int[] table = new int[TABLE];      // gram -> id + 1, 0 = not seen
        private final Long2IntOpenHashMap other = new Long2IntOpenHashMap();
        private long[] gramOf = new long[4096];
        private int[][] post = new int[4096][];
        private int[] size = new int[4096];
        private int[] stamp = new int[4096];
        private int[] codes = new int[64];
        private int ids;
        int entries;

        public GramIndex() {
            other.defaultReturnValue(-1);
        }

        public void add(String key) {
            int e = entries++;
            int st = e + 1;
            int len = key.length();
            int[] d = codes;
            if (len > d.length) codes = d = new int[Math.max(len, d.length * 2)];
            for (int i = 0; i < len; i++) {
                int c = key.charAt(i) - 0x20;
                d[i] = c >= 0 && c < R ? c : -1;
            }
            for (int gl = 1; gl <= 3 && gl <= len; gl++) {
                for (int i = 0; i <= len - gl; i++) {
                    long gram;
                    int idx;
                    long c0 = key.charAt(i);
                    if (gl == 1) {
                        gram = 1L << 48 | c0 << 32;
                        idx = d[i];
                    } else if (gl == 2) {
                        gram = 2L << 48 | c0 << 32 | (long) key.charAt(i + 1) << 16;
                        idx = (d[i] | d[i + 1]) < 0 ? -1 : B2 + d[i] * R + d[i + 1];
                    } else {
                        gram = 3L << 48 | c0 << 32 | (long) key.charAt(i + 1) << 16 | key.charAt(i + 2);
                        idx = (d[i] | d[i + 1] | d[i + 2]) < 0 ? -1 : B3 + (d[i] * R + d[i + 1]) * R + d[i + 2];
                    }
                    int id = id(gram, idx);
                    if (stamp[id] == st) continue;
                    stamp[id] = st;
                    int s = size[id];
                    int[] p = post[id];
                    if (s == p.length) post[id] = p = Arrays.copyOf(p, s < 4 ? 4 : s + (s >> 1));
                    p[s] = e;
                    size[id] = s + 1;
                }
            }
        }

        private int id(long gram, int idx) {
            int id;
            if (idx >= 0) {
                id = table[idx] - 1;
                if (id >= 0) return id;
                id = ids++;
                table[idx] = id + 1;
            } else {
                id = other.get(gram);
                if (id >= 0) return id;
                id = ids++;
                other.put(gram, id);
            }
            if (id == gramOf.length) {
                int n = id * 2;
                gramOf = Arrays.copyOf(gramOf, n);
                post = Arrays.copyOf(post, n);
                size = Arrays.copyOf(size, n);
                stamp = Arrays.copyOf(stamp, n);
            }
            gramOf[id] = gram;
            post[id] = new int[1];
            return id;
        }

        /** The final gram map, laid out as the original's (see the class comment). */
        public Long2ObjectOpenHashMap<int[]> assemble() {
            Long2ObjectOpenHashMap<int[]> mutable = new Long2ObjectOpenHashMap<>();
            for (int id = 0; id < ids; id++) {
                int[] p = post[id];
                mutable.put(gramOf[id], size[id] == p.length ? p : Arrays.copyOf(p, size[id]));
            }
            Long2ObjectOpenHashMap<int[]> byGram = new Long2ObjectOpenHashMap<>(mutable.size());
            ObjectIterator<Long2ObjectMap.Entry<int[]>> it = mutable.long2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                Long2ObjectMap.Entry<int[]> en = it.next();
                byGram.put(en.getLongKey(), en.getValue());
            }
            return byGram;
        }
    }
}
