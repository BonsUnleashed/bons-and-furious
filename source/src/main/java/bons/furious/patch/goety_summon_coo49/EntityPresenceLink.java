package bons.furious.patch.goety_summon_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.apache.logging.log4j.LogManager;

/**
 * Link to the shared layer vanilla_entity_walk_skip (EntityWalks, group client_shake_gate), bound by name so this group
 * compiles on its own. The two methods the switch needs (markUnused, unmark) are bound together: when one is absent
 * neither is bound, nothing is marked and every search runs in full.
 */
final class EntityPresenceLink {
    private static final MethodHandle MARK_UNUSED, UNMARK;

    static {
        MethodHandle mu = null, um = null;
        MethodHandles.Lookup l = MethodHandles.lookup();
        try {
            Class<?> w = Class.forName("bons.furious.patch.client_shake_gate.EntityWalks", false, EntityPresenceLink.class.getClassLoader());
            MethodHandle unused = l.findStatic(w, "markUnused", MethodType.methodType(boolean.class, String.class, Object.class, Class.class));
            MethodHandle clear = l.findStatic(w, "unmark", MethodType.methodType(void.class));
            mu = unused;
            um = clear;
        } catch (Throwable t) {
            LogManager.getLogger("Bons and Furious").warn("Bons and Furious: cannot reach vanilla_entity_walk_skip ({}); the switches of group goety_summon_coo49 run the stock code", t.toString());
        }
        MARK_UNUSED = mu;
        UNMARK = um;
    }

    private EntityPresenceLink() {
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
