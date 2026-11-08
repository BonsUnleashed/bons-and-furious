package bons.furious.patch.mcreator_pv_c49;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mcreator_shared_default_player_variables (MCreator mods, both sides, since 1.0.49). Idea:
 * Collections Of Optimizations (mcreator.shareDefaultPlayerVariables), idea text only. Our own logic; no mod code.
 *
 * Goblin's Tyranny 1.2.4 for NeoForge 1.21.1 reads a player variable as
 * {@code Optional.of(GoblinsTyrannyModVariables.get(entity)).orElse(new PlayerVariables()).field}: the argument of orElse is
 * evaluated first, so every read allocates a complete default PlayerVariables even though get(entity) (the entity's
 * player_variables data attachment, created with its default on the first read) is never null and the default is thrown
 * away.
 *
 * What changes: at every read site PvRewrite accepted (the exact shape NEW; DUP; INVOKESPECIAL <init>()V; INVOKEVIRTUAL
 * Optional.orElse; CHECKCAST; GETFIELD f, where the constructor leaves f holding a constant), the three instructions that
 * build the default become one invokedynamic linked here: it answers one default per PlayerVariables class, built once by
 * the class's own constructor.
 *
 * Why it is identical: Optional.orElse returns its argument only when the Optional is empty and stores it nowhere; the
 * result is cast and one field is read from it at once, so the shared object is never written and never escapes; the
 * field read is one whose value after the constructor is a constant (number, string literal or null; never written
 * otherwise in the constructor), so the shared default holds exactly what a fresh one holds (strings are the same interned
 * literal). The constructor has no other effect than its field stores, and the class is initialised at the same moment
 * (the first read). Sites that write into the result, read a field the constructor fills with an object, or have any other
 * shape keep allocating.
 *
 * -Dbons_and_furious.mcreatorSharedDefaultPlayerVariables=false links every rewritten site to the constructor (a new
 * default per read, as stock); setEnabled switches linked sites at run time (verification harness). .shadow=true links
 * every site to a check that builds a fresh default, compares the site's field with the shared default's (raw bits,
 * identity for references), counts any difference and returns the fresh default (stock behaviour).
 */
public final class SharedDefaults {
    public static final String KEY = "mcreator_shared_default_player_variables";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mcreatorSharedDefaultPlayerVariables", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mcreatorSharedDefaultPlayerVariables.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    public static final AtomicLong LINKED = new AtomicLong();
    private static final Map<Class<?>, Site> SITES = new ConcurrentHashMap<>();
    private static final MethodHandle SHADOW_CHECK;
    private static volatile boolean announced, warned;

    static {
        try {
            SHADOW_CHECK = MethodHandles.lookup().findStatic(SharedDefaults.class, "shadow", MethodType.methodType(Object.class, Site.class, Field.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private SharedDefaults() {
    }

    /** One PlayerVariables class: the site every rewritten read of it links to, the shared default and the constructor. */
    static final class Site {
        final MutableCallSite site;
        final MethodHandle shared, fresh;
        final Object instance;

        Site(MutableCallSite site, MethodHandle shared, MethodHandle fresh, Object instance) {
            this.site = site;
            this.shared = shared;
            this.fresh = fresh;
            this.instance = instance;
        }
    }

    /** Bootstrap of every rewritten site: name "pv_" + the field the site reads, type ()PlayerVariables. */
    public static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type) throws Throwable {
        Class<?> pv = type.returnType();
        MethodHandle fresh = caller.findConstructor(pv, MethodType.methodType(void.class)).asType(type);
        try {
            Site s = site(pv, fresh);
            LINKED.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (MCreator player-variable reads share one default per mod instead of allocating one per read){}",
                        KEY, SHADOW ? " - shadow verification on" : "");
            }
            if (SHADOW) {
                Field f = pv.getField(name.startsWith("pv_") ? name.substring(3) : name);
                return new ConstantCallSite(MethodHandles.insertArguments(SHADOW_CHECK, 0, s, f).asType(type));
            }
            return s.site;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Bons and Furious: {} could not link a read of {} ({}); that site builds its default as before", KEY, pv.getName(), t.toString());
            }
            return new ConstantCallSite(fresh);
        }
    }

    private static Site site(Class<?> pv, MethodHandle fresh) throws Throwable {
        Site s = SITES.get(pv);
        if (s != null) return s;
        synchronized (SITES) {
            s = SITES.get(pv);
            if (s == null) {
                Object instance = fresh.invoke();
                MethodHandle shared = MethodHandles.constant(pv, instance);
                MutableCallSite site = new MutableCallSite(MethodType.methodType(pv));
                site.setTarget(enabled ? shared : fresh);
                s = new Site(site, shared, fresh, instance);
                SITES.put(pv, s);
            }
            return s;
        }
    }

    /** Switches every linked site (and later links) between the shared default and the constructor. */
    public static synchronized void setEnabled(boolean on) {
        enabled = on;
        MutableCallSite[] all = new MutableCallSite[SITES.size()];
        int i = 0;
        for (Site s : SITES.values()) {
            s.site.setTarget(on ? s.shared : s.fresh);
            if (i < all.length) all[i++] = s.site;
        }
        if (i > 0) MutableCallSite.syncAll(i == all.length ? all : java.util.Arrays.copyOf(all, i));
    }

    /** The shared default of a PlayerVariables class (verification harness), or null when no site of it linked yet. */
    public static Object sharedOf(Class<?> pv) {
        Site s = SITES.get(pv);
        return s == null ? null : s.instance;
    }

    @SuppressWarnings("unused")
    private static Object shadow(Site s, Field f) throws Throwable {
        Object fresh = s.fresh.invoke();
        SHADOW_CHECKS.incrementAndGet();
        Object a = f.get(s.instance), b = f.get(fresh);
        boolean same;
        if (f.getType() == float.class) same = Float.floatToRawIntBits((Float) a) == Float.floatToRawIntBits((Float) b);
        else if (f.getType() == double.class) same = Double.doubleToRawLongBits((Double) a) == Double.doubleToRawLongBits((Double) b);
        else if (f.getType().isPrimitive()) same = a.equals(b);
        else same = a == b;
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: {} shadow mismatch: {}.{} is {} in the shared default but {} in a fresh one", KEY,
                    f.getDeclaringClass().getName(), f.getName(), a, b);
        return fresh;
    }
}
