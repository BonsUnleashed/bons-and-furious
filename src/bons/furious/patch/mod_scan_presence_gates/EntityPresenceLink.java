package bons.furious.patch.mod_scan_presence_gates;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;

/**
 * Link to the shared entity layers vanilla_entity_class_count_layer (EntityClassCounts, group crittersandcompanions_c2) and
 * vanilla_entity_walk_skip (EntityWalks, group client_shake_gate), bound by name so this group compiles on its own; every
 * method answers "unknown" / does nothing when a layer class is absent.
 */
final class EntityPresenceLink {
    private static final MethodHandle TRACKED_INDEX, NONE, COUNT, MARK_EMPTY, MARK_UNUSED, UNMARK;

    static {
        MethodHandle ti = null, none = null, count = null, me = null, mu = null, um = null;
        MethodHandles.Lookup l = MethodHandles.lookup();
        try {
            Class<?> c = Class.forName("bons.furious.patch.crittersandcompanions_c2.EntityClassCounts", false, EntityPresenceLink.class.getClassLoader());
            ti = l.findStatic(c, "trackedIndex", MethodType.methodType(int.class, String.class));
            none = l.findStatic(c, "none", MethodType.methodType(boolean.class, Level.class, int.class));
            count = l.findStatic(c, "count", MethodType.methodType(int.class, Level.class, int.class));
            Class<?> w = Class.forName("bons.furious.patch.client_shake_gate.EntityWalks", false, EntityPresenceLink.class.getClassLoader());
            me = l.findStatic(w, "markEmpty", MethodType.methodType(boolean.class, String.class, Object.class, Class.class, int.class));
            mu = l.findStatic(w, "markUnused", MethodType.methodType(boolean.class, String.class, Object.class, Class.class));
            um = l.findStatic(w, "unmark", MethodType.methodType(void.class));
        } catch (Throwable t) {
            LogManager.getLogger("Bons and Furious").warn("Bons and Furious: cannot reach vanilla_entity_class_count_layer / vanilla_entity_walk_skip ({}); the gates of group mod_scan_presence_gates run the stock code", t.toString());
        }
        TRACKED_INDEX = ti;
        NONE = none;
        COUNT = count;
        MARK_EMPTY = me;
        MARK_UNUSED = mu;
        UNMARK = um;
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
