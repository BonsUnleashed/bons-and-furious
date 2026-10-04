package bons.furious.patch.dh_loader;

import com.seibel.distanthorizons.core.util.objects.pooling.StringPool;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch distanthorizons_pooled_string_index (Distant Horizons 3.3.2, LGPL-3.0; both sides). No Distant
 * Horizons code here.
 *
 * When DH reads an LOD data source (FullDataPointIdMap.deserialize) it turns every id-map entry's biome and block-state
 * text into a String through StringPool.INSTANCE.getPooledString: a trie with one node per character, each step a
 * StampedLock optimistic read plus a ConcurrentHashMap<Character, TrieNode> lookup, so a 90-character block-state string
 * costs about 90 dependent hash lookups. The pool exists to hand out ONE canonical String per text (DH's wrapper caches
 * are keyed by these strings).
 *
 * This index sits in front of the trie: a content-hash table (open addressing, the String.hashCode polynomial over the
 * characters, full character comparison on a hash match) that only ever holds Strings the trie itself returned. A text the
 * table does not know goes to the trie, and the trie's answer is remembered and returned. So every String handed out is
 * the trie's canonical instance for that text, with the switch on or off, before or after a toggle: DH's pool is never
 * cleared in 3.3.2 (StringPool.clear has no caller), and if anything does clear it the index steps aside for good
 * (poolCleared), because the trie would then create new instances. The empty text keeps DH's own path (it returns the
 * literal ""). The trie is still filled exactly as before (it is asked once per distinct text).
 *
 * Concurrency: the table is written under a lock and read without one. Strings are immutable with final fields, so a
 * reader that sees a reference sees the whole String; a reader that misses an entry being inserted asks the trie, which
 * returns the same canonical instance. A reader can never return a String whose text differs: it compares every
 * character.
 */
public final class PooledStringIndex {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.dhPooledStringIndex=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhPooledStringIndex", "true"));
    /** Shadow mode for rigs: every String the index returns is also asked of DH's trie and compared by identity (WARN on a difference). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.dhPooledStringIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Texts remembered (distinct strings learned from the trie); read by probes. */
    public static final AtomicLong REMEMBERED = new AtomicLong();
    private static final int MAX_CAPACITY = 1 << 20;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Object LOCK = new Object();
    /** Open-addressing table: STRINGS[i] is null or a canonical String, HASHES[i] its String.hashCode. Replaced (never mutated in place) on growth. */
    private static volatile Table table = new Table(1 << 12);
    private static int size;                       // guarded by LOCK
    private static volatile boolean poolCleared;
    private static volatile boolean announced;

    private static final class Table {
        final String[] strings;
        final int[] hashes;
        final int mask;

        Table(int capacity) {
            strings = new String[capacity];
            hashes = new int[capacity];
            mask = capacity - 1;
        }
    }

    private PooledStringIndex() {
    }

    /** In place of pool.getPooledString(chars) in FullDataPointIdMap.deserialize. */
    public static String pooled(StringPool pool, CharArrayList chars) {
        if (!enabled || poolCleared) return pool.getPooledString(chars);
        int length = chars.size();
        if (length == 0) return pool.getPooledString(chars);
        char[] a = chars.elements();
        int h = 0;
        for (int i = 0; i < length; i++) h = 31 * h + a[i];
        Table t = table;
        String[] strings = t.strings;
        int[] hashes = t.hashes;
        int mask = t.mask;
        for (int i = (h ^ (h >>> 16)) & mask; ; i = (i + 1) & mask) {
            String s = strings[i];
            if (s == null) break;
            if (hashes[i] == h && same(s, a, length)) return SHADOW ? shadow(pool, chars, s) : s;
        }
        String canonical = pool.getPooledString(chars);
        remember(canonical, h);
        return canonical;
    }

    private static boolean same(String s, char[] a, int length) {
        if (s.length() != length) return false;
        for (int i = 0; i < length; i++) {
            if (s.charAt(i) != a[i]) return false;
        }
        return true;
    }

    private static void remember(String canonical, int h) {
        synchronized (LOCK) {
            if (poolCleared) return;
            Table t = table;
            if ((size + 1) * 2 > t.strings.length) {
                if (t.strings.length >= MAX_CAPACITY) return;     // full: further texts are answered by the trie alone
                Table bigger = new Table(t.strings.length * 2);
                for (int i = 0; i < t.strings.length; i++) {
                    if (t.strings[i] != null) put(bigger, t.strings[i], t.hashes[i]);
                }
                t = bigger;
            }
            if (put(t, canonical, h)) {
                size++;
                REMEMBERED.incrementAndGet();
            }
            table = t;    // volatile write: publishes the new entry (and a grown table) to later readers
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_pooled_string_index answers repeated LOD id-map strings from a table in front of Distant Horizons' string pool");
        }
    }

    private static boolean put(Table t, String s, int h) {
        for (int i = (h ^ (h >>> 16)) & t.mask; ; i = (i + 1) & t.mask) {
            String e = t.strings[i];
            if (e == null) {
                t.hashes[i] = h;          // the hash first: a reader that sees the String sees a matching or older slot hash,
                t.strings[i] = s;         // and a stale hash only makes it miss (then the trie answers)
                return true;
            }
            if (e == s) return false;
        }
    }

    /** StringPool.clear() was called: the trie may create new instances from now on, so the index answers nothing more. */
    public static void poolCleared() {
        synchronized (LOCK) {
            if (!poolCleared) {
                poolCleared = true;
                table = new Table(2);
                size = 0;
                LOGGER.warn("Bons and Furious: Distant Horizons' string pool was cleared; distanthorizons_pooled_string_index steps aside until restart");
            }
        }
    }

    /** Shadow mode: the pool's own answer is returned; a different instance from the index is counted and logged. */
    private static String shadow(StringPool pool, CharArrayList chars, String indexed) {
        SHADOW_CHECKS.incrementAndGet();
        String original = pool.getPooledString(chars);
        if (original != indexed && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: distanthorizons_pooled_string_index shadow check: the index returned another instance than the pool for [{}] (equal text: {})",
                    original, original.equals(indexed));
        return original;
    }
}
