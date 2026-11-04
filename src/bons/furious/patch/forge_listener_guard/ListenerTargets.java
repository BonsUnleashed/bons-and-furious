package bons.furious.patch.forge_listener_guard;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.ClassLoaderFactory;
import net.minecraftforge.eventbus.IEventListenerFactory;
import net.minecraftforge.eventbus.ModLauncherFactory;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * forge_listener_prologue_guard: which method an eventbus 6.2.33 ASMEventHandler really calls, and its code.
 *
 * An @SubscribeEvent listener is an ASMEventHandler (or its anonymous subclass for events that cannot be cancelled) whose
 * `handler` is a wrapper class eventbus generates for one method (ClassLoaderFactory.transformNode: checkcast the event,
 * then INVOKESTATIC / INVOKEVIRTUAL / INVOKEINTERFACE of the method on the wrapper's `instance` field).
 *  - Forge's bus (ModLauncherFactory): the wrapper class is generated from ModLauncherFactory.PENDING, wrapper name ->
 *    the Method; that entry IS the method the wrapper calls (also when two methods share a unique name, where the
 *    handler's own description would name the second one).
 *  - Other buses (ClassLoaderFactory, the offline harness): the method named by the handler's description
 *    ("ASM: " + target + " " + name + descriptor), accepted only when the factory's cache maps it to exactly this wrapper.
 * Then: the wrapper's name must be the factory's unique name for the method, and for an instance listener the method
 * that runs is the one virtual dispatch selects on the listener object's class. Generic-filtered handlers and every
 * other listener class (lambdas, phase markers, other mods' wrappers) are never resolved, so never guarded.
 * eventbus is an open module: its private fields are read through privateLookupIn / setAccessible, and its internal
 * Cache (not exported to us) only through reflection.
 */
public final class ListenerTargets {
    /** A resolved listener: impl = the method that runs; cancelCheck = ASMEventHandler's own cancel test applies. */
    public record Target(Method impl, boolean isStatic, boolean cancelCheck, boolean receiveCanceled, String name) {}

    private static final VarHandle HANDLER, SUB_INFO, FILTER;
    private static final Field PENDING, CL_CACHE;
    private static final Method CACHE_GET;
    private static final String NON_CANCELABLE = "net.minecraftforge.eventbus.ASMEventHandler$1";

    static {
        VarHandle h = null, s = null, f = null;
        Field p = null, c = null;
        Method g = null;
        try {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(ASMEventHandler.class, MethodHandles.lookup());
            h = l.findVarHandle(ASMEventHandler.class, "handler", IEventListener.class);
            s = l.findVarHandle(ASMEventHandler.class, "subInfo", SubscribeEvent.class);
            f = l.findVarHandle(ASMEventHandler.class, "filter", java.lang.reflect.Type.class);
            p = ModLauncherFactory.class.getDeclaredField("PENDING");
            p.setAccessible(true);
            c = ClassLoaderFactory.class.getDeclaredField("cache");
            c.setAccessible(true);
            g = Class.forName("net.minecraftforge.eventbus.internal.Cache", false, ASMEventHandler.class.getClassLoader()).getMethod("get", Object.class);
            g.setAccessible(true);
        } catch (Throwable t) {
            h = s = f = null;
            p = c = null;
            g = null;
        }
        HANDLER = h;
        SUB_INFO = s;
        FILTER = f;
        PENDING = p;
        CL_CACHE = c;
        CACHE_GET = g;
    }

    private ListenerTargets() {}

    static boolean available() {
        return HANDLER != null && SUB_INFO != null && FILTER != null && PENDING != null && CL_CACHE != null && CACHE_GET != null;
    }

    /** The resolved target, or null with the reason in why[0]. Reads only; loads no class that is not loaded already. */
    public static Target resolve(IEventListener l, IEventListenerFactory factory, String[] why) {
        try {
            if (!available()) return fail(why, "eventbus internals not readable");
            boolean cancelCheck;
            if (l.getClass() == ASMEventHandler.class) cancelCheck = true;
            else if (l.getClass().getName().equals(NON_CANCELABLE) && l.getClass().getSuperclass() == ASMEventHandler.class) cancelCheck = false;
            else return fail(why, l instanceof net.minecraftforge.eventbus.api.EventPriority ? "phase marker" : "not an @SubscribeEvent handler (" + l.getClass().getName() + ")");
            ASMEventHandler h = (ASMEventHandler) l;
            if (FILTER.get(h) != null) return fail(why, "generic filter");
            SubscribeEvent sub = (SubscribeEvent) SUB_INFO.get(h);
            IEventListener handler = (IEventListener) HANDLER.get(h);
            if (sub == null || handler == null) return fail(why, "no handler");
            Class<?> wrapper = handler.getClass();
            String readable = l.toString();
            if (readable == null || !readable.startsWith("ASM: ")) return fail(why, "unexpected description");
            int sp = readable.lastIndexOf(' ');
            Method declared;
            if (factory instanceof ModLauncherFactory) {
                Object pending = CACHE_GET.invoke(PENDING.get(null), wrapper.getName());
                if (!(pending instanceof Method m)) return fail(why, "no pending wrapper entry");
                declared = m;
            } else if (factory != null && factory.getClass() == ClassLoaderFactory.class) {
                declared = cachedMethodOf(wrapper);
                if (declared == null) return fail(why, "method not found");
                if (CACHE_GET.invoke(CL_CACHE.get(null), declared) != wrapper) return fail(why, "wrapper made for another method");
            } else {
                return fail(why, "unknown listener factory");
            }
            if (sp < 0) return fail(why, "unexpected description");
            if (!wrapper.getName().equals(factory.getUniqueName(declared))) return fail(why, "wrapper name differs");
            if (!readable.endsWith(" " + declared.getName() + Type.getMethodDescriptor(declared))) return fail(why, "description names another method");
            Field instanceField = null;
            for (Field f : wrapper.getDeclaredFields()) if (f.getName().equals("instance")) instanceField = f;
            boolean isStatic = Modifier.isStatic(declared.getModifiers());
            if (isStatic != (instanceField == null)) return fail(why, "wrapper shape differs");
            Method impl = declared;
            if (isStatic) {
                // the description is built from the registered method's class: equal only for the method the wrapper calls
                if (!readable.equals("ASM: " + declared.getDeclaringClass() + " " + declared.getName() + Type.getMethodDescriptor(declared)))
                    return fail(why, "description names another class");
            } else {
                instanceField.setAccessible(true);
                Object instance = instanceField.get(handler);
                if (instance == null) return fail(why, "no instance");
                if (declared.getDeclaringClass().isInterface()) return fail(why, "interface method");
                if (!declared.getDeclaringClass().isInstance(instance)) return fail(why, "instance of another class");
                // the method INVOKEVIRTUAL selects on the instance's class: a public method (the annotated one is public,
                // an override cannot be less visible), found through the public-method view eventbus' own registration
                // (getMethods) already built, so no class is loaded for this
                impl = instance.getClass().getMethod(declared.getName(), declared.getParameterTypes());
                if (Modifier.isAbstract(impl.getModifiers()) || Modifier.isStatic(impl.getModifiers()) || impl.isBridge()
                        || impl.getDeclaringClass().isInterface() || !declared.getDeclaringClass().isAssignableFrom(impl.getDeclaringClass())
                        || !Type.getMethodDescriptor(impl).equals(Type.getMethodDescriptor(declared)))
                    return fail(why, "instance method not resolved");
            }
            return new Target(impl, isStatic, cancelCheck, sub.receiveCanceled(), impl.getDeclaringClass().getName() + "." + impl.getName());
        } catch (Throwable t) {
            return fail(why, "not resolvable (" + t.getClass().getSimpleName() + ")");
        }
    }

    /**
     * ClassLoaderFactory only (other buses, the offline harness): the method whose wrapper class the factory's cache holds
     * (a reverse look-up of the cache's own map), or null.
     */
    private static Method cachedMethodOf(Class<?> wrapper) throws ReflectiveOperationException {
        Object cache = CL_CACHE.get(null);
        Field mf = null;
        for (Class<?> c = cache.getClass(); c != null && mf == null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) if (f.getName().equals("map")) mf = f;
        }
        if (mf == null) return null;
        mf.setAccessible(true);
        Object map = mf.get(cache);
        if (!(map instanceof java.util.Map<?, ?> m)) return null;
        Method found = null;
        for (java.util.Map.Entry<?, ?> e : new java.util.ArrayList<>(m.entrySet())) {
            if (e.getValue() == wrapper && e.getKey() instanceof Method k) {
                if (found != null) return null;   // two methods share the wrapper: unknown
                found = k;
            }
        }
        return found;
    }

    /** The listener object of an instance @SubscribeEvent handler (the wrapper's `instance` field), else null. */
    static Object instance(IEventListener l) {
        try {
            IEventListener handler = (IEventListener) HANDLER.get((ASMEventHandler) l);
            for (Field f : handler.getClass().getDeclaredFields()) {
                if (f.getName().equals("instance")) {
                    f.setAccessible(true);
                    return f.get(handler);
                }
            }
        } catch (Throwable t) {
            // not an instance handler
        }
        return null;
    }

    private static Target fail(String[] why, String reason) {
        if (why != null && why.length > 0) why[0] = reason;
        return null;
    }

    /** Harness only (plain JVM): where the class bytes come from. */
    static volatile java.util.function.Function<Class<?>, byte[]> bytesOverride;

    public static void harnessBytes(java.util.function.Function<Class<?>, byte[]> f) {
        bytesOverride = f;
    }

    /** The method's code as its class file has it (null if not readable or the file is not this class). */
    static MethodNode methodNode(Method impl) {
        Class<?> c = impl.getDeclaringClass();
        byte[] bytes = classBytes(c);
        if (bytes == null) return null;
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, ClassReader.SKIP_FRAMES);
        if (!n.name.equals(c.getName().replace('.', '/'))) return null;
        String desc = Type.getMethodDescriptor(impl);
        for (MethodNode m : n.methods) {
            if (m.name.equals(impl.getName()) && m.desc.equals(desc) && Modifier.isStatic(m.access) == Modifier.isStatic(impl.getModifiers())) return m;
        }
        return null;
    }

    static byte[] classBytes(Class<?> c) {
        java.util.function.Function<Class<?>, byte[]> o = bytesOverride;
        if (o != null) return o.apply(c);
        String res = c.getName().replace('.', '/') + ".class";
        try {
            Module mod = c.getModule();
            if (mod != null && mod.isNamed()) {
                try (InputStream in = mod.getResourceAsStream(res)) {
                    if (in != null) return in.readAllBytes();
                }
            }
            ClassLoader l = c.getClassLoader();
            try (InputStream in = l != null ? l.getResourceAsStream(res) : ClassLoader.getSystemResourceAsStream(res)) {
                return in == null ? null : in.readAllBytes();
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
