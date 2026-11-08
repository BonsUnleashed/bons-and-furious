package bons.furious.patch.createsolar_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch createsolar_goggles_reflection_memo (Create: Solar Powered 1.2.7 for Minecraft 1.21.1, MIT; client
 * side). Ours; no Create: Solar Powered code is carried.
 *
 * What the mod does. ClientIntegration.onRenderGui (every frame, RenderGuiEvent.Post) resolves Create's goggles class with
 * Class.forName and asks it for isWearing(Player) with getMethod, then invokes it. Create 6.0.10's GogglesItem
 * (com.simibubi.create.content.equipment.goggles; the com.simibubi.create.api.equipment.goggles class the probe tries first
 * does not exist) has no isWearing method (it is isWearingGoggles), so every frame getMethod builds a NoSuchMethodException
 * with its stack trace, the mod's catch swallows it and returns, and the overlay never draws.
 *
 * What the switch does. The Class.forName answer is kept per name once it succeeded; the getMethod answer is kept for the
 * (class, name, parameter types) it was asked for: the Method, or the miss, which is then thrown again as one prepared
 * exception without a stack trace (the mod's catch block discards it either way).
 *
 * Why the result is identical. A class name resolved through the same class loader always yields the same Class object
 * (and is initialised by the first call), and a loaded class's public methods never change, so getMethod gives an equal
 * Method (a fresh copy in the stock path, invoked the same way) or the same miss every time. ClassNotFoundException is not
 * kept (the next frame asks again, as stock); only the exception object of a miss differs, and the mod never reads it.
 *
 * -Dbons_and_furious.createsolarGogglesReflectionMemo=false switches it off at run time.
 */
public final class GogglesMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.createsolarGogglesReflectionMemo", "true"));
    private static volatile boolean announced;
    /** {name, Class} of the last successful Class.forName. */
    private static volatile Object[] lastClass;
    /** {owner Class, name, parameter types, Method or Miss} of the last getMethod. */
    private static volatile Object[] lastMethod;

    private GogglesMemo() {}

    /** A getMethod miss thrown again from the memo: no stack trace (the caller's catch discards it). */
    static final class Miss extends NoSuchMethodException {
        Miss(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    public static Class<?> forName(String name, Operation<Class<?>> original) {
        Object[] m = lastClass;
        if (m != null && m[0].equals(name)) return (Class<?>) m[1];
        Class<?> c = original.call(name);
        if (c != null && name != null) lastClass = new Object[] {name, c};
        return c;
    }

    public static Method getMethod(Class<?> owner, String name, Class<?>[] params, Operation<Method> original) {
        Object[] m = lastMethod;
        if (m != null && m[0] == owner && m[1].equals(name) && Arrays.equals((Object[]) m[2], params)) {
            announce();
            if (m[3] instanceof Method method) return method;
            throw sneaky((Throwable) m[3]);
        }
        Method found;
        try {
            found = original.call(owner, name, params);
        } catch (Throwable t) {
            if (t instanceof NoSuchMethodException && owner != null && name != null && params != null)
                lastMethod = new Object[] {owner, name, params.clone(), new Miss(t.getMessage())};
            throw sneaky(t);
        }
        if (found != null && owner != null && name != null && params != null) lastMethod = new Object[] {owner, name, params.clone(), found};
        return found;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: createsolar_goggles_reflection_memo applies (Create: Solar Powered's goggles lookup is resolved once)");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneaky(Throwable t) throws T {
        throw (T) t;
    }
}
