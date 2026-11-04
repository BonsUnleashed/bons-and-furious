package bons.furious.patch.goety_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;

/**
 * Link to the shared entity layers vanilla_entity_class_count_layer (EntityClassCounts, group crittersandcompanions_c2) and
 * vanilla_entity_walk_skip (EntityWalks, group client_shake_gate), bound by name so this group compiles on its own; every
 * method answers "unknown" / does nothing when a layer class is absent.
 */
final class EntityPresenceLink {
    private static final MethodHandle TRACKED_INDEX, NONE, COUNT, MARK_EMPTY, MARK_UNUSED, UNMARK;
    /** A level's tracked-class counts array (CountedLevel.bons$classCountStorage().bons$classCounts()), or null. */
    private static final MethodHandle LEVEL_COUNTS;

    static {
        MethodHandle ti = null, none = null, count = null, me = null, mu = null, um = null, lc = null;
        MethodHandles.Lookup l = MethodHandles.lookup();
        ClassLoader loader = EntityPresenceLink.class.getClassLoader();
        try {
            Class<?> c = Class.forName("bons.furious.patch.crittersandcompanions_c2.EntityClassCounts", false, loader);
            ti = l.findStatic(c, "trackedIndex", MethodType.methodType(int.class, String.class));
            none = l.findStatic(c, "none", MethodType.methodType(boolean.class, Level.class, int.class));
            count = l.findStatic(c, "count", MethodType.methodType(int.class, Level.class, int.class));
            Class<?> w = Class.forName("bons.furious.patch.client_shake_gate.EntityWalks", false, loader);
            me = l.findStatic(w, "markEmpty", MethodType.methodType(boolean.class, String.class, Object.class, Class.class, int.class));
            mu = l.findStatic(w, "markUnused", MethodType.methodType(boolean.class, String.class, Object.class, Class.class));
            um = l.findStatic(w, "unmark", MethodType.methodType(void.class));
        } catch (Throwable t) {
            LogManager.getLogger("Bons and Furious").warn("Bons and Furious: cannot reach vanilla_entity_class_count_layer / vanilla_entity_walk_skip ({}); the gates of group goety_coo49 run the stock code", t.toString());
        }
        try {
            Class<?> counted = Class.forName("bons.furious.patch.crittersandcompanions_c2.EntityClassCounts$CountedLevel", false, loader);
            Class<?> storage = Class.forName("bons.furious.patch.crittersandcompanions_c2.EntityClassCounts$Storage", false, loader);
            MethodHandle st = l.findVirtual(counted, "bons$classCountStorage", MethodType.methodType(storage));
            MethodHandle co = l.findVirtual(storage, "bons$classCounts", MethodType.methodType(int[].class));
            lc = MethodHandles.filterReturnValue(st, co).asType(MethodType.methodType(int[].class, Object.class));
        } catch (Throwable t) {
            lc = null;              // no fast stand-aside: every search with an instance present asks none()
        }
        TRACKED_INDEX = ti;
        NONE = none;
        COUNT = count;
        MARK_EMPTY = me;
        MARK_UNUSED = mu;
        UNMARK = um;
        LEVEL_COUNTS = lc;
    }

    private EntityPresenceLink() {
    }

    static int trackedIndex(String name) {
        try {
            return TRACKED_INDEX == null ? -1 : (int) TRACKED_INDEX.invokeExact(name);
        } catch (Throwable t) {
            return -1;
        }
    }

    static boolean none(Level level, int index) {
        try {
            return NONE != null && index >= 0 && (boolean) NONE.invokeExact(level, index);
        } catch (Throwable t) {
            return false;
        }
    }

    static int count(Level level, int index) {
        try {
            return COUNT == null || index < 0 ? -1 : (int) COUNT.invokeExact(level, index);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** The counts array of the level last asked, the level held weakly (no level outlives its world through this). */
    private record Last(WeakReference<Object> level, int[] counts) {}

    private static final Last NOTHING_YET = new Last(new WeakReference<>(null), null);
    /** Plain field: a race only reads the counts of another level, which the caller uses only to run its search in full. */
    private static Last last = NOTHING_YET;

    /**
     * True when the layer's count for tracked entry {@code index} in {@code level} is not 0, read straight from that level's
     * counts array (cached by level: otherwise the path where an instance exists would ask none() and all its
     * checks on every search). The caller then runs its search in full, which is what none() answering false makes it do;
     * an answer of false proves nothing, and the caller still asks none(), with all its checks, before it marks anything. A
     * level's counts array is made once, with its entity storage, and only its owner thread changes it; a racy read of a
     * stale or another level's count can only make the caller run a search in full that it could have marked.
     */
    static boolean held(Object level, int index) {
        if (index < 0) return false;
        Last l = last;
        int[] c = l.level.get() == level ? l.counts : refill(level);
        return c != null && index < c.length && c[index] != 0;
    }

    private static int[] refill(Object level) {
        int[] c = null;
        if (LEVEL_COUNTS != null && level != null) {
            try {
                c = (int[]) LEVEL_COUNTS.invokeExact(level);
            } catch (Throwable t) {
                c = null;           // not a counted level (or no storage): none() answers for it
            }
        }
        last = new Last(new WeakReference<>(level), c);
        return c;
    }

    static boolean markEmpty(String key, Object level, Class<?> base, int index) {
        try {
            return MARK_EMPTY != null && index >= 0 && (boolean) MARK_EMPTY.invokeExact(key, level, base, index);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean markUnused(String key, Object level, Class<?> base) {
        try {
            return MARK_UNUSED != null && (boolean) MARK_UNUSED.invokeExact(key, level, base);
        } catch (Throwable t) {
            return false;
        }
    }

    static void unmark() {
        try {
            if (UNMARK != null) UNMARK.invokeExact();
        } catch (Throwable t) {
            // nothing was marked
        }
    }
}
