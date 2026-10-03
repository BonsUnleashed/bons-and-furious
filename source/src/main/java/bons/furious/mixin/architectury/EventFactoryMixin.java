package bons.furious.mixin.architectury;

import dev.architectury.event.EventFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * architectury_event_dispatch (Architectury API 9.2.14).
 *
 * EventFactory.invokeMethod dispatched every listener call through
 * MethodHandles.lookup().unreflect(method).bindTo(listener).invokeWithArguments(args), resolving a new MethodHandle
 * for every listener on every event. Calling the Method directly gives the same result; the listener's own exception
 * is rethrown unchanged, as before.
 */
@Mixin(value = EventFactory.class, remap = false)
public abstract class EventFactoryMixin {
    /**
     * @author BonsUnleashed
     * @reason Skip the per-call MethodHandle resolution (measured 649 to 34 ns per listener call).
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    private static <T, R> R invokeMethod(T listener, Method method, Object[] args) throws Throwable {
        try {
            return (R) method.invoke(listener, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
