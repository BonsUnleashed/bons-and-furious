package bons.furious.patch.structurify;

import java.util.Map;

/**
 * Bons and Furious switch structurify_set_data_single_lookup (Structurify 2.0.34+mc1.20.1). No Structurify code here.
 *
 * Structurify's RandomSpreadUtil.getStructureSetData answers every spacing, separation, salt and frequency question of
 * every structure placement check. It asks its structure-set map containsKey(id) and then get(id): two walks of a TreeMap
 * keyed by structure set id. The containsKey is now answered with one get whose value is handed to the get that follows
 * (same thread, same map object, same key object); a missing key, or one mapped to null, answers false, and the method
 * returns null in both cases exactly as before. Any other get (another map, another key, nothing remembered) is a real
 * get, so a config reload between the two calls still reads the new map.
 */
public final class SetDataLookup {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.structurifySetDataLookup=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.structurifySetDataLookup", "true"));

    private static final class Last {
        Map<?, ?> map;
        Object key, value;
    }

    private static final ThreadLocal<Last> LAST = ThreadLocal.withInitial(Last::new);

    private SetDataLookup() {
    }

    /** In place of getStructureSetData's containsKey: one get, remembered for the get that follows a true answer. */
    public static boolean containsKey(Map<?, ?> map, Object key) {
        if (!enabled) return map.containsKey(key);
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

    /** In place of the get that follows: the value just read for this map and key, otherwise a real get. */
    public static Object get(Map<?, ?> map, Object key) {
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
}
