package bons.furious.patch.distanthorizons_level_wrappers;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch distanthorizons_keyed_level_pins (Distant Horizons 3.3.2, client only). [FIX] Distant Horizons'
 * cache of server-keyed level wrappers no longer keeps the last ClientLevel of every dimension the player has left.
 *
 * On a server with Distant Horizons, DH wraps each client level in a ServerKeyedClientLevelWrapper_neoforge and caches it in
 * KeyedClientLevelManager_neoforge.keyedLevelsCache, a synchronized WeakHashMap keyed by the ClientLevel. The wrapper holds
 * its ClientLevel in a final field, so the map's value keeps its own key reachable and the weak key never clears (the
 * WeakHashMap caveat): an entry leaves only when the server announces the same dimension again (setServerKeyedLevel
 * removes that dimension's entries) or the connection ends (clearKeyedLevel). So after a dimension change the previous
 * level stays in memory until that dimension is visited again: one old ClientLevel, with its LocalPlayer, per other
 * dimension visited (with planets that is one per planet).
 *
 * Here getServerKeyedLevel stores the wrapper in the map behind a weak reference and pins it on its ClientLevel instead
 * (an array field a mixin adds to ClientLevel: pairs map, wrapper). While anything but the cache references the level,
 * the level references its wrapper, so the cache answers exactly as before: the same wrapper object for the level, created
 * once, until DH itself removes the entry; setServerKeyedLevel's removeIf and clearKeyedLevel work on the keys and are
 * unchanged. Once the level is unreachable from outside, level and wrapper are unreachable together, the WeakHashMap drops
 * the entry, and no caller could have asked for that level anyway (it would have to hold it). A level whose entry DH
 * removed keeps its last pin until it is collected; a new entry for the same level replaces the pin.
 *
 * Runtime switch -Dbons_and_furious.distanthorizonsKeyedLevelPins=false (read at every store): the wrapper is stored as
 * DH stores it; entries stored either way are read correctly, so the switch may be flipped at any time.
 * -Dbons_and_furious.distanthorizonsKeyedLevelPins.shadow=true: every cache hit checks that its weak reference still holds
 * the wrapper and that the level's pin for this map is that same wrapper (SHADOW_CHECKS, SHADOW_MISMATCHES, the first 20
 * logged; expect 0).
 */
public final class KeyedLevelPins {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.distanthorizonsKeyedLevelPins", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.distanthorizonsKeyedLevelPins.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Wrappers stored weakly and pinned (tests, rigs). */
    public static final AtomicLong PINNED = new AtomicLong();
    private static final Object LOCK = new Object();
    private static volatile boolean announced;

    private KeyedLevelPins() {
    }

    /** Duck interface the ClientLevel mixin implements: pairs (map, wrapper), copy-on-write under LOCK. */
    public interface Holder {
        Object[] bons$dhKeyedPins();

        void bons$setDhKeyedPins(Object[] pins);
    }

    /** The cache's value with the switch on: the wrapper, weakly (the level pins it). */
    static final class Ref extends WeakReference<Object> {
        Ref(Object wrapper) {
            super(wrapper);
        }
    }

    /** getServerKeyedLevel, after keyedLevelsCache.get(level): the wrapper DH stored (a Ref is opened). */
    public static Object get(Map<?, ?> cache, Object level, Object stored) {
        if (!(stored instanceof Ref r)) return stored;
        Object wrapper = r.get();
        if (SHADOW) shadowCheck(cache, level, wrapper);
        return wrapper;
    }

    /** getServerKeyedLevel, at keyedLevelsCache.put(level, wrapper): what to store for the wrapper. */
    public static Object valueFor(Map<?, ?> cache, Object level, Object wrapper) {
        if (!enabled || wrapper == null || !(level instanceof Holder h)) return wrapper;
        synchronized (LOCK) {
            Object[] old = h.bons$dhKeyedPins();
            int at = indexOf(old, cache);
            Object[] pins;
            if (at >= 0) {
                pins = old.clone();
                pins[at + 1] = wrapper;
            } else {
                int n = old == null ? 0 : old.length;
                pins = new Object[n + 2];
                if (n > 0) System.arraycopy(old, 0, pins, 0, n);
                pins[n] = cache;
                pins[n + 1] = wrapper;
            }
            h.bons$setDhKeyedPins(pins);
        }
        PINNED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_keyed_level_pins applies (Distant Horizons' keyed level cache keeps a wrapper only "
                    + "while its ClientLevel is in use){}", SHADOW ? " - shadow verification on" : "");
        }
        return new Ref(wrapper);
    }

    /** For tests: the wrapper a level carries for a cache map, or null. */
    public static Object pinned(Object level, Map<?, ?> cache) {
        if (!(level instanceof Holder h)) return null;
        Object[] pins = h.bons$dhKeyedPins();
        int at = indexOf(pins, cache);
        return at < 0 ? null : pins[at + 1];
    }

    private static int indexOf(Object[] pins, Object cache) {
        if (pins != null) for (int i = 0; i < pins.length; i += 2) if (pins[i] == cache) return i;
        return -1;
    }

    private static void shadowCheck(Map<?, ?> cache, Object level, Object wrapper) {
        SHADOW_CHECKS.incrementAndGet();
        Object pin = pinned(level, cache);
        if (wrapper == null || pin != wrapper) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20)
                LOGGER.warn("Bons and Furious: distanthorizons_keyed_level_pins shadow mismatch #{}: level {} reads wrapper {} from the cache, its pin is {}",
                        n, level, wrapper, pin);
        }
    }
}
