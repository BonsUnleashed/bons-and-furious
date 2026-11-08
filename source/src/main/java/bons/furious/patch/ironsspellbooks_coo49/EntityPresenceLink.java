package bons.furious.patch.ironsspellbooks_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;

/**
 * Link to the shared entity layer vanilla_entity_class_count_layer (EntityClassCounts, group crittersandcompanions_c2),
 * bound by name so this group compiles on its own; every method answers "unknown" when the layer class or one of its
 * methods is absent (the gate of this group then runs the stock code).
 */
final class EntityPresenceLink {
    private static final MethodHandle TRACKED_INDEX, NONE, COUNT;

    static {
        MethodHandle ti = null, none = null, count = null;
        MethodHandles.Lookup l = MethodHandles.lookup();
        try {
            Class<?> c = Class.forName("bons.furious.patch.crittersandcompanions_c2.EntityClassCounts", false, EntityPresenceLink.class.getClassLoader());
            ti = l.findStatic(c, "trackedIndex", MethodType.methodType(int.class, String.class));
            none = l.findStatic(c, "none", MethodType.methodType(boolean.class, Level.class, int.class));
            count = l.findStatic(c, "count", MethodType.methodType(int.class, Level.class, int.class));
        } catch (Throwable t) {
            ti = none = count = null;
            LogManager.getLogger("Bons and Furious").warn("Bons and Furious: cannot reach vanilla_entity_class_count_layer ({}); the gate of group ironsspellbooks_coo49 runs the stock code", t.toString());
        }
        TRACKED_INDEX = ti;
        NONE = none;
        COUNT = count;
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
}
