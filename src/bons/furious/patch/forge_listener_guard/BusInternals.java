package bons.furious.patch.forge_listener_guard;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.IEventListenerFactory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventExceptionHandler;

/**
 * forge_listener_prologue_guard and the live listener census: the private state of one eventbus 6.2.33 EventBus, read
 * once (eventbus is an open module): its bus id (the index of its listener arrays in every ListenerList), the dispatch
 * type check and marker type, whether phases are tracked, its exception handler, its listener factory, and a handle on
 * its volatile shutdown flag (read on every post, as EventBus.post reads it). Only a bus whose class is exactly EventBus,
 * on an eventbus whose shape BusShape has verified, is accepted.
 */
final class BusInternals {
    final EventBus bus;
    final int busId;
    final boolean checkTypes, trackPhases;
    final Class<?> baseType;
    final IEventExceptionHandler handler;
    final IEventListenerFactory factory;
    static final VarHandle SHUTDOWN;

    static {
        VarHandle s = null;
        try {
            s = MethodHandles.privateLookupIn(EventBus.class, MethodHandles.lookup()).findVarHandle(EventBus.class, "shutdown", boolean.class);
        } catch (Throwable t) {
            s = null;
        }
        SHUTDOWN = s;
    }

    private BusInternals(EventBus bus, int busId, boolean checkTypes, boolean trackPhases, Class<?> baseType, IEventExceptionHandler handler,
                         IEventListenerFactory factory) {
        this.bus = bus;
        this.busId = busId;
        this.checkTypes = checkTypes;
        this.trackPhases = trackPhases;
        this.baseType = baseType;
        this.handler = handler;
        this.factory = factory;
    }

    /** The bus's state, or null with the reason in why[0]. */
    static BusInternals of(IEventBus b, String[] why) {
        try {
            if (b == null) return fail(why, "no bus");
            if (b.getClass() != EventBus.class) return fail(why, "the bus is a " + b.getClass().getName());
            String shape = BusShape.check();
            if (shape != null) return fail(why, "eventbus differs from 6.2.33 (" + shape + ")");
            if (!ListenerTargets.available() || SHUTDOWN == null) return fail(why, "eventbus internals are not readable");
            EventBus eb = (EventBus) b;
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(EventBus.class, MethodHandles.lookup());
            int id = (int) l.findVarHandle(EventBus.class, "busID", int.class).get(eb);
            boolean ct = (boolean) l.findVarHandle(EventBus.class, "checkTypesOnDispatch", boolean.class).get(eb);
            boolean tp = (boolean) l.findVarHandle(EventBus.class, "trackPhases", boolean.class).get(eb);
            Class<?> base = (Class<?>) l.findVarHandle(EventBus.class, "baseType", Class.class).get(eb);
            IEventExceptionHandler h = (IEventExceptionHandler) l.findVarHandle(EventBus.class, "exceptionHandler", IEventExceptionHandler.class).get(eb);
            IEventListenerFactory f = (IEventListenerFactory) l.findVarHandle(EventBus.class, "factory", IEventListenerFactory.class).get(eb);
            if (h == null || base == null || f == null) return fail(why, "the bus is not set up as eventbus 6.2.33 sets it up");
            return new BusInternals(eb, id, ct, tp, base, h, f);
        } catch (Throwable t) {
            return fail(why, t.toString());
        }
    }

    private static BusInternals fail(String[] why, String reason) {
        if (why != null && why.length > 0) why[0] = reason;
        return null;
    }

    /** The Forge bus (or the harness bus); never throws. */
    static IEventBus forgeBus() {
        try {
            java.util.function.Supplier<IEventBus> t = GuardedDispatch.testBus;
            return t != null ? t.get() : net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        } catch (Throwable t) {
            return null;
        }
    }
}
