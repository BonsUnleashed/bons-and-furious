package bons.furious.patch.structurify;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch structurify_set_data_single_lookup (Structurify 2.0.42 for NeoForge 1.21.1, both sides). No
 * Structurify code here.
 *
 * Structurify's RandomSpreadUtil.getStructureSetData answers every spacing, separation, salt, frequency, placement-attempt
 * and weight question of every structure placement check. It asks its structure-set map containsKey(id) and then get(id):
 * two walks of a TreeMap keyed by structure set id (about a thousand sets in a large pack, so about ten string comparisons
 * per walk).
 *
 * 1.0.23: the containsKey is answered with one get whose value is handed to the get that follows (same thread, same map
 * object, same key object); a missing key, or one mapped to null, answers false, and the method returns null in both cases
 * exactly as before. Any other get (another map, another key, nothing remembered) is a real get.
 *
 * Since 1.0.49 both answers come from a hash index of the map instead of a TreeMap walk: a HashMap holding the same keys
 * and the same value objects, built from the map the first time it is asked and rebuilt whenever the map asked is another
 * object or holds another number of entries. Why identical: Structurify builds a new map on every config load and never
 * writes a map after publishing it (StructurifyConfig.load / loadFromJson take WorldgenDataProvider.loadStructureSets'
 * freshly built TreeMap, or Collections.emptyMap() without a registry; the config screens, the serializer, StructureSetMixin
 * and RandomSpreadUtil only read it; the guard rows pin those methods), the keys are Strings compared by natural order
 * (TreeMap with no comparator), for which compareTo == 0 exactly when equals, and the index hands out the very value
 * objects the map holds (Structurify changes settings inside those objects, which the index sees). Maps of any other
 * class, a TreeMap with a comparator and maps holding a non-String key are not indexed: they are asked directly, as in
 * 1.0.23. Shadow (rigs): -Dbons_and_furious.structurifySetDataIndex.shadow=true compares every index answer with the map's
 * own get (SHADOW_CHECKS / SHADOW_MISMATCHES, up to 20 WARN lines).
 */
public final class SetDataLookup {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.structurifySetDataLookup=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.structurifySetDataLookup", "true"));
    /** Since 1.0.49: the hash index part; -Dbons_and_furious.structurifySetDataIndex=false keeps the 1.0.23 behaviour (one TreeMap walk). */
    public static volatile boolean indexEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.structurifySetDataIndex", "true"));
    /** Since 1.0.49 (rigs): every index answer is checked against the map's own get. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.structurifySetDataIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Map<?, ?> EMPTY_MAP = Collections.emptyMap();

    private static final class Last {
        Map<?, ?> map;
        Object key, value;
    }

    private static final ThreadLocal<Last> LAST = ThreadLocal.withInitial(Last::new);

    /** One map object's index: valid while that object is the map asked and it holds {@code size} entries. */
    private static final class Index {
        final Map<?, ?> source;
        final int size;
        final HashMap<Object, Object> table;

        Index(Map<?, ?> source, int size, HashMap<Object, Object> table) {
            this.source = source;
            this.size = size;
            this.table = table;
        }
    }

    /** The last index built (immutable once published); null = none yet. */
    private static volatile Index index;
    /** A map we will not index (another class, a comparator, a non-String key): asked directly until another map comes. */
    private static volatile Map<?, ?> notIndexable;
    private static volatile boolean announced;

    private SetDataLookup() {
    }

    /**
     * In place of getStructureSetData's containsKey. Since 1.0.49: the index's answer (present with a non-null value);
     * 1.0.23 (index off): one get, remembered for the get that follows a true answer.
     */
    public static boolean containsKey(Map<?, ?> map, Object key) {
        if (!enabled) return map.containsKey(key);
        if (indexEnabled) return lookup(map, key) != null;   // absent or mapped to null: the method returns null either way
        Object value = map.get(key);
        Last l = LAST.get();
        if (value == null) {
            l.map = null;
            l.key = null;
            return false;                                  // absent or mapped to null: the method returns null either way
        }
        l.map = map;
        l.key = key;
        l.value = value;
        return true;
    }

    /**
     * In place of the get that follows. Since 1.0.49: the index's answer. 1.0.23 (index off): the value just read for this
     * map and key, otherwise a real get.
     */
    public static Object get(Map<?, ?> map, Object key) {
        if (enabled && indexEnabled) return lookup(map, key);
        Last l = LAST.get();
        if (l.map == map && l.key == key) {
            Object v = l.value;
            l.map = null;
            l.key = null;
            l.value = null;
            return v;
        }
        return map.get(key);
    }

    /** map.get(key) answered from the map's hash index (built or rebuilt here when needed). */
    static Object lookup(Map<?, ?> map, Object key) {
        Index ix = index;
        if (ix == null || ix.source != map || ix.size != map.size()) {
            if (notIndexable == map) return map.get(key);
            ix = build(map);
            if (ix == null) return map.get(key);
        }
        Object v = ix.table.get(key);
        if (SHADOW) shadow(map, key, v);
        return v;
    }

    /** A new index of the map, published; null (and the map remembered as not indexable) when it may not be indexed. */
    private static Index build(Map<?, ?> map) {
        boolean plainTree = map.getClass() == TreeMap.class && ((TreeMap<?, ?>) map).comparator() == null;
        if (!plainTree && map != EMPTY_MAP) {
            notIndexable = map;
            return null;
        }
        int size = map.size();
        HashMap<Object, Object> table = new HashMap<>(Math.max(16, (int) (size / 0.75f) + 1));
        try {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof String)) {     // compareTo and equals agree only for String keys
                    notIndexable = map;
                    return null;
                }
                table.put(e.getKey(), e.getValue());
            }
        } catch (RuntimeException changing) {               // written by another thread while copied: ask it directly
            return null;
        }
        if (table.size() != size || map.size() != size) { // changed while it was copied: ask it directly this time
            return null;
        }
        Index ix = new Index(map, size, table);
        index = ix;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: structurify_set_data_single_lookup: structure-set settings answered from a hash index ({} sets)", size);
        }
        return ix;
    }

    private static void shadow(Map<?, ?> map, Object key, Object v) {
        SHADOW_CHECKS.incrementAndGet();
        Object real = map.get(key);
        if (real != v && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: structurify_set_data_single_lookup SHADOW: set {} answered {} by the index, {} by the map", key, v, real);
    }
}
