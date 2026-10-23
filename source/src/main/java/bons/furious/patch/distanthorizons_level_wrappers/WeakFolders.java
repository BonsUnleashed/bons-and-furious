package bons.furious.patch.distanthorizons_level_wrappers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Bons and Furious switch distanthorizons_save_folder_weak_keys (Distant Horizons 3.3.2, client only). [FIX] A level
 * wrapper that nothing else uses any more no longer keeps its old ClientLevel in memory through Distant Horizons' save
 * folder map.
 *
 * DH's ClientOnlySaveStructure (one per server connection, DhClientWorld.saveStructure) answers getSaveFolder(wrapper) with
 * levelWrapperToFileMap.computeIfAbsent(wrapper, DH's own function): the folder is computed once per wrapper object (the
 * wrapper classes do not override equals or hashCode, so the map compares by identity) and the entry is never removed.
 * Every client level gets a new wrapper (ServerKeyedClientLevelWrapper_neoforge on a server with DH, ClientLevelWrapper_neoforge
 * otherwise), and the wrapper holds its ClientLevel in a final field, so after each dimension change the old ClientLevel
 * (with its LocalPlayer, chunks and entities) stays reachable from the map until the player leaves the server. A revisit
 * is a new wrapper and gets a new entry, so the old entries are never read again; only toString lists them (the INFO line
 * "Started DHLevel for ... with saves at [...]").
 *
 * Here the computeIfAbsent call in getSaveFolder goes to a table of this structure that holds the wrapper weakly and the
 * File strongly, a ConcurrentHashMap like DH's own whose keys hash by the wrapper's identity hash code (the hash code DH's
 * map uses) and compare by identity. While a wrapper is reachable from anywhere but the table, getSaveFolder returns the
 * very same File object, and DH's function runs once per wrapper, with the wrapper as its argument, in the same bin lock
 * discipline; exceptions and a null result behave as in ConcurrentHashMap. Entries are never removed, as in DH's map: once
 * a wrapper is collected its entry keeps its File and its hash (so the table keeps exactly the entry count, bins and
 * iteration order DH's map would have) and simply can never be found again, which no caller could do anyway (it would have
 * to hold the wrapper). toString's values() call reads the table's values, so DH's log line is byte-for-byte the same.
 * A key whose class overrides equals or hashCode (none in DH 3.3.2) is handed to DH's own map unchanged (toString then
 * lists the table's entries before DH's). Which store a structure uses is decided at its first call and kept for its
 * lifetime.
 *
 * Runtime switch -Dbons_and_furious.distanthorizonsSaveFolderWeakKeys=false (read at a structure's first call): DH's own
 * map, as shipped. -Dbons_and_furious.distanthorizonsSaveFolderWeakKeys.shadow=true (verification runs): every answer of
 * the table is also put into DH's own map (which then keeps the levels, by design, and gives toString its text) and must be
 * the object DH's map holds for that wrapper, and every toString checks that the table renders the same text as DH's map
 * (SHADOW_CHECKS, SHADOW_MISMATCHES, the first 20 logged; expect 0).
 */
public final class WeakFolders {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.distanthorizonsSaveFolderWeakKeys", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.distanthorizonsSaveFolderWeakKeys.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** DH folder computations run through a table (tests, rigs). */
    public static final AtomicLong COMPUTED = new AtomicLong();
    /** A structure that uses DH's own map (the switch was off at its first call). */
    public static final Object STOCK = new Object();
    private static final Object INIT_LOCK = new Object();
    private static volatile boolean announced;
    /** true when a class (or a superclass below Object) overrides equals(Object) or hashCode(). */
    private static final ClassValue<Boolean> OWN_EQUALITY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("equals", Object.class).getDeclaringClass() != Object.class
                        || type.getMethod("hashCode").getDeclaringClass() != Object.class;
            } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
                return Boolean.TRUE;   // cannot tell: keep DH's own map for this class
            }
        }
    };

    private WeakFolders() {
    }

    /** Duck interface the ClientOnlySaveStructure mixin implements: the structure's store (null until its first call). */
    public interface Owner {
        Object bons$folders();

        void bons$setFolders(Object store);
    }

    /** The weak key: the wrapper's identity hash code and identity equality; a collected wrapper equals nothing else. */
    static final class Key extends WeakReference<Object> {
        private final int hash;

        Key(Object wrapper) {
            super(wrapper);
            this.hash = System.identityHashCode(wrapper);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Key k)) return false;
            Object a = get();
            return a != null && a == k.get();
        }
    }

    /** One structure's folders: weak wrapper -> DH's File. */
    public static final class Table {
        final ConcurrentHashMap<Key, Object> map = new ConcurrentHashMap<>();

        @SuppressWarnings({"unchecked", "rawtypes"})
        Object computeIfAbsent(Object wrapper, Function fn) {
            Key key = new Key(wrapper);
            Object v = map.get(key);
            if (v != null) return v;
            return map.computeIfAbsent(key, k -> {
                Object folder = fn.apply(wrapper);            // DH's own function, with the wrapper as its argument
                COMPUTED.incrementAndGet();
                return folder;                                // null: no mapping, null returned (ConcurrentHashMap's rule)
            });
        }

        /** For tests: entries whose wrapper is still reachable. */
        public int liveEntries() {
            int n = 0;
            for (Key k : map.keySet()) if (k.get() != null) n++;
            return n;
        }

        public int entries() {
            return map.size();
        }
    }

    private static Object store(Object owner) {
        Owner o = (Owner) owner;
        Object s = o.bons$folders();
        if (s != null) return s;
        synchronized (INIT_LOCK) {
            s = o.bons$folders();
            if (s == null) {
                s = enabled ? new Table() : STOCK;
                o.bons$setFolders(s);
                if (s != STOCK && !announced) {
                    announced = true;
                    LOGGER.info("Bons and Furious: distanthorizons_save_folder_weak_keys applies (Distant Horizons' save folder map no longer keeps "
                            + "the level wrappers, and through them old ClientLevels, after they are unloaded){}", SHADOW ? " - shadow verification on" : "");
                }
            }
            return s;
        }
    }

    /** ClientOnlySaveStructure.getSaveFolder, around levelWrapperToFileMap.computeIfAbsent(wrapper, function). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object computeIfAbsent(Object owner, ConcurrentHashMap dhMap, Object key, Function fn, Operation<Object> original) {
        Object s = store(owner);
        if (s == STOCK || key == null || OWN_EQUALITY.get(key.getClass())) return original.call(dhMap, key, fn);
        Object folder = ((Table) s).computeIfAbsent(key, fn);
        if (SHADOW && folder != null) shadowAnswer(dhMap, key, folder);
        return folder;
    }

    /** ClientOnlySaveStructure.toString, around levelWrapperToFileMap.values(). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Collection<?> values(Object owner, ConcurrentHashMap dhMap, Operation<Collection<?>> original) {
        Object s = store(owner);
        if (s == STOCK) return original.call(dhMap);
        Table t = (Table) s;
        Collection<?> ours = t.map.values();
        if (SHADOW) {
            // DH's map holds every answer in shadow mode (in DH's own order): it is the reference, and the table must
            // render the same text whenever it holds the same keys (no key with its own equality went to DH's map)
            Collection<?> dh = original.call(dhMap);
            if (dh.size() == t.map.size()) shadowText(dh, ours);
            return dh;
        }
        if (dhMap.isEmpty()) return ours;
        // keys with their own equals/hashCode went to DH's map (none in DH 3.3.2): list both, the table's entries first
        List<Object> all = new ArrayList<>(ours);
        all.addAll(original.call(dhMap));
        return all;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void shadowAnswer(ConcurrentHashMap dhMap, Object key, Object folder) {
        SHADOW_CHECKS.incrementAndGet();
        Object before = dhMap.putIfAbsent(key, folder);
        if (before != null && before != folder) mismatch("wrapper " + key + " got " + folder + " but DH's map holds " + before + " for it");
    }

    private static void shadowText(Collection<?> dh, Collection<?> ours) {
        SHADOW_CHECKS.incrementAndGet();
        String a = join(dh), b = join(ours);
        if (!a.equals(b)) mismatch("save folder list differs: DH [" + a + "], table [" + b + "]");
    }

    private static String join(Collection<?> c) {
        StringBuilder sb = new StringBuilder();
        for (Object o : c) {
            if (sb.length() > 0) sb.append(';');
            sb.append(o);
        }
        return sb.toString();
    }

    private static void mismatch(String what) {
        long n = SHADOW_MISMATCHES.incrementAndGet();
        if (n <= 20) LOGGER.warn("Bons and Furious: distanthorizons_save_folder_weak_keys shadow mismatch #{}: {}", n, what);
    }
}
