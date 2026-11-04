package bons.furious.patch.forge_listener_guard;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventListener;
import org.objectweb.asm.Type;

/**
 * The live listener census of MinecraftForge.EVENT_BUS (Forge 47.4.28, eventbus 6.2.33; both sides), shared with other
 * Bons and Furious switches: "which listeners would a post of this event class reach right now?". It only reads; it
 * works whether or not forge_listener_prologue_guard is enabled. Other groups reach it through MethodHandles looked up by
 * name (this group's classes are not on their compile path) and treat every "unknown" answer as "run the stock code".
 *
 * A watch names an event class and an allow-list of listener methods ("binary.class.Name|method|descriptor" of the code
 * that runs: for an instance listener the method virtual dispatch selects on the listener object). Every answer reads
 * the event class's live listener array for the Forge bus (ListenerList.getListeners with the bus id, read from the bus
 * as SharksSetup reads it: exactly what the next post walks; eventbus builds a new array whenever a listener is added
 * or removed) and is cached per array object:
 *   {@link #mask}: -1 unknown (census off or unavailable, a listener that is not an @SubscribeEvent handler resolvable
 *       to its method - a lambda, another factory's wrapper, a generic filter -, a method not on the allow-list, or a
 *       class a mixin or transformer changed according to ModLauncher's audit trail); otherwise bit i set when allowed[i]
 *       is registered at least once (0 = phase markers only).
 *   {@link #onlyPhaseMarkers}: true only when the array holds nothing but EventPriority phase markers (a post reaches no
 *       listener; false = there is one, or unknown).
 *   {@link #realListeners}: the number of non-marker listeners, -1 unknown.
 *   {@link #listeners}: the live array itself (do not change it), null unknown.
 * The answer is about the array this call read; a listener registered on another thread afterwards is seen by the next
 * call, the same way the bus's own posts read the array.
 *
 * -Dbons_and_furious.forgeListenerCensus=false makes every answer unknown.
 */
public final class ListenerCensus {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.forgeListenerCensus", "true"));
    /** Arrays examined (a new verdict computed); for the harness and diagnostics. */
    public static final AtomicLong VERDICTS = new AtomicLong();
    private static volatile BusInternals bi;
    private static volatile boolean resolved;

    /** One watched event class with its allow-list; the last verdict is kept with the array it was computed for. */
    static final class Watch {
        final Class<?> eventClass;
        final ListenerList list;
        final String[] allowed;
        volatile Verdict last;

        Watch(Class<?> eventClass, ListenerList list, String[] allowed) {
            this.eventClass = eventClass;
            this.list = list;
            this.allowed = allowed;
        }
    }

    private record Verdict(IEventListener[] array, long mask, int real) {}

    private ListenerCensus() {}

    /** Harness: forget the resolved bus (GuardedDispatch.harnessBus calls this). */
    static synchronized void reset() {
        bi = null;
        resolved = false;
    }

    private static BusInternals bus() {
        BusInternals b = bi;
        if (b != null || resolved) return b;
        synchronized (ListenerCensus.class) {
            if (!resolved) {
                String[] why = new String[1];
                bi = BusInternals.of(BusInternals.forgeBus(), why);
                resolved = true;
                if (bi == null) GuardedDispatch.LOGGER.warn("Bons and Furious: the Forge listener census is unavailable ({}); the switches that ask it run as shipped", why[0]);
            }
            return bi;
        }
    }

    /**
     * A watch on an event class (allowed: up to 63 entries "binary.class.Name|method|descriptor", null = none), or null
     * when the census is unavailable. The event class's ListenerList is looked up through EventListenerHelper here (the
     * same list a post of that class walks), so ask lazily: on first use, not in a mixin plugin or a static initialiser
     * that runs while classes are being loaded.
     */
    public static Object watch(Class<?> eventClass, String[] allowed) {
        try {
            if (!enabled || eventClass == null || bus() == null) return null;
            String[] a = allowed == null ? new String[0] : allowed.clone();
            if (a.length > 63) return null;
            for (String s : a) if (s == null) return null;
            ListenerList list = EventListenerHelper.getListenerList(eventClass);
            return list == null ? null : new Watch(eventClass, list, a);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The verdict for the live array (computed when the array object changed), null when unknown. */
    private static Verdict current(Object watch) {
        if (!(watch instanceof Watch w) || !enabled) return null;
        BusInternals b = bi;
        if (b == null) return null;
        try {
            IEventListener[] array = w.list.getListeners(b.busId);
            Verdict v = w.last;
            if (v == null || v.array != array) {
                v = verdict(w, array, b);
                w.last = v;
            }
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    /** See the class comment: -1 unknown, else the bits of the allow-listed methods present (0 = no real listener). */
    public static long mask(Object watch) {
        Verdict v = current(watch);
        return v == null ? -1L : v.mask;
    }

    /** True only when the live array holds nothing but phase markers (a post reaches no listener). */
    public static boolean onlyPhaseMarkers(Object watch) {
        Verdict v = current(watch);
        return v != null && v.real == 0;
    }

    /** The number of real (non phase marker) listeners in the live array, -1 when unknown. */
    public static int realListeners(Object watch) {
        Verdict v = current(watch);
        return v == null ? -1 : v.real;
    }

    /** The live listener array the next post would walk (shared with the bus: never change it), null when unknown. */
    public static IEventListener[] listeners(Object watch) {
        Verdict v = current(watch);
        return v == null ? null : v.array;
    }

    private static Verdict verdict(Watch w, IEventListener[] array, BusInternals b) {
        VERDICTS.incrementAndGet();
        long mask = 0L;
        int real = 0;
        java.util.List<String> allowed = Arrays.asList(w.allowed);
        for (IEventListener l : array) {
            if (l instanceof EventPriority) continue;
            real++;
            if (mask < 0) continue;
            ListenerTargets.Target t = ListenerTargets.resolve(l, b.factory, null);
            if (t == null) {
                mask = -1L;
                continue;
            }
            String key = t.impl().getDeclaringClass().getName() + "|" + t.impl().getName() + "|" + Type.getMethodDescriptor(t.impl());
            int i = allowed.indexOf(key);
            if (i < 0 || !AuditTrail.untransformed(t.impl().getDeclaringClass().getName(), false)) {
                mask = -1L;
                continue;
            }
            mask |= 1L << i;
        }
        return new Verdict(array, mask, real);
    }

    /** Harness: the bus the census reads (after GuardedDispatch.harnessBus). */
    static IEventBus currentBus() {
        BusInternals b = bus();
        return b == null ? null : b.bus;
    }
}
