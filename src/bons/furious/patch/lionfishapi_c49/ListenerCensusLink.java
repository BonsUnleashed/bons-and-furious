package bons.furious.patch.lionfishapi_c49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.apache.logging.log4j.LogManager;

/** Link to the shared live listener census (ListenerCensus of the forge_listener_guard group), bound by name so this group
 * compiles on its own; every answer is "unknown" (run the stock code) when the census class is absent or unavailable. */
final class ListenerCensusLink {
    private static final MethodHandle WATCH, MASK, ONLY, REAL;

    static {
        MethodHandle w = null, m = null, o = null, r = null;
        MethodHandles.Lookup l = MethodHandles.lookup();
        try {
            Class<?> c = Class.forName("bons.furious.patch.forge_listener_guard.ListenerCensus", false, ListenerCensusLink.class.getClassLoader());
            w = l.findStatic(c, "watch", MethodType.methodType(Object.class, Class.class, String[].class));
            m = l.findStatic(c, "mask", MethodType.methodType(long.class, Object.class));
            o = l.findStatic(c, "onlyPhaseMarkers", MethodType.methodType(boolean.class, Object.class));
            r = l.findStatic(c, "realListeners", MethodType.methodType(int.class, Object.class));
        } catch (Throwable t) {
            LogManager.getLogger("Bons and Furious").warn("Bons and Furious: cannot reach the Forge listener census ({}); the gates of group lionfishapi_c49 run the stock code", t.toString());
        }
        WATCH = w; MASK = m; ONLY = o; REAL = r;
    }

    private ListenerCensusLink() {}

    static Object watch(Class<?> eventClass, String[] allowed) {
        try { return WATCH == null ? null : (Object) WATCH.invokeExact(eventClass, allowed); } catch (Throwable t) { return null; }
    }
    static long mask(Object watch) {
        try { return MASK == null || watch == null ? -1L : (long) MASK.invokeExact(watch); } catch (Throwable t) { return -1L; }
    }
    static boolean onlyPhaseMarkers(Object watch) {
        try { return ONLY != null && watch != null && (boolean) ONLY.invokeExact(watch); } catch (Throwable t) { return false; }
    }
    static int realListeners(Object watch) {
        try { return REAL == null || watch == null ? -1 : (int) REAL.invokeExact(watch); } catch (Throwable t) { return -1; }
    }
}
