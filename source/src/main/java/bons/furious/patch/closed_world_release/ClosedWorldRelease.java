package bons.furious.patch.closed_world_release;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch closed_world_reference_release (fix, since 1.0.49; Minecraft 1.21.1 on NeoForge 21.1.252, client).
 * Idea: Collections Of Optimizations (vanilla.releaseClosedWorld), idea text only. No Citadel code is carried: this class
 * only reads and clears its field by reflection.
 *
 * What leaks. Citadel 2.7.1 (1.21.1): EntityLinkButton.renderedEntities, a private static HashMap<EntityType<?>, Entity>
 * that the button's renderWidget, its only writer, fills with type.create(Minecraft.getInstance().level) for every entity
 * type it has no preview of yet and never clears: the first preview entity of every entity link in a Citadel book keeps the
 * ClientLevel it was created in (with its chunks and entities) on the title screen and next to the next world, until the
 * game is restarted.
 *
 * What the switch does, only when the class is already loaded (never loading it early) and its field is a static Map (the
 * guard fingerprints EntityLinkButton's static initialiser and renderWidget): when the client leaves its world
 * (Minecraft.disconnect, or Minecraft.clearClientLevel when the server sends the client back to configuration, right
 * before Minecraft.level becomes null) Citadel's map is cleared. Its entries are only previews for book pages:
 * renderWidget creates one again with the current level the next time a page draws that entity link. Nothing else
 * changes: no other field is read or written, no event, packet or saved data differs.
 *
 * -Dbons_and_furious.closedWorldReferenceRelease=false keeps every reference as before.
 * -Dbons_and_furious.closedWorldReferenceRelease.shadow=true (verification runs only): nothing is released; every reference
 * the switch would release is counted in SHADOW_MISMATCHES (a reference to a closed world the mod did not drop itself) and
 * logged once, SHADOW_CHECKS counts the release passes.
 */
public final class ClosedWorldRelease {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.closedWorldReferenceRelease", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.closedWorldReferenceRelease.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** References released (in shadow mode: found) since the game started. */
    public static final AtomicLong CITADEL = new AtomicLong();

    static final String CITADEL_LINK = "com.github.alexthe666.citadel.client.gui.EntityLinkButton";

    /** Parts that failed once (WARN logged): left alone until the game is restarted. */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final Set<String> ANNOUNCED = ConcurrentHashMap.newKeySet();
    /** ModLauncher's TransformingClassLoader.getLoadedClass(String) (public); null in other class loaders. */
    private static final Method LOADED_CLASS = loadedClassMethod();

    private ClosedWorldRelease() {
    }

    /** Minecraft.disconnect / clearClientLevel, right before the level field is cleared (client thread): the client leaves its level. */
    public static void clientLevelClosing(Object level) {
        if (!enabled) return;
        pass();
        part(CITADEL_LINK, ClosedWorldRelease::citadelLinks);
    }

    private interface Part {
        int run() throws Throwable;
    }

    private static void pass() {
        if (SHADOW) SHADOW_CHECKS.incrementAndGet();
    }

    private static void part(String owner, Part p) {
        if (FAILED.contains(owner)) return;
        try {
            int n = p.run();
            if (n > 0 && ANNOUNCED.add(owner)) {
                LOGGER.info("Bons and Furious: closed_world_reference_release {} {} reference{} to a closed world kept by {}",
                        SHADOW ? "found (shadow mode, nothing released)" : "released", n, n == 1 ? "" : "s", owner);
            }
            if (SHADOW && n > 0) SHADOW_MISMATCHES.addAndGet(n);
        } catch (Throwable t) {
            FAILED.add(owner);
            LOGGER.warn("Bons and Furious: closed_world_reference_release leaves {} alone until the game is restarted ({})", owner, t.toString());
        }
    }

    // ---- Citadel: EntityLinkButton.renderedEntities ------------------------------------------------------------------

    private static int citadelLinks() throws ReflectiveOperationException {
        Class<?> c = loaded(CITADEL_LINK);
        if (c == null) return 0;                                   // no book page drew an entity link: nothing kept
        Map<?, ?> map = staticMap(c, "renderedEntities");
        if (map == null || map.isEmpty()) return 0;
        int n = map.size();
        if (!SHADOW) map.clear();
        CITADEL.addAndGet(n);
        return n;
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    /** A static Map field of a loaded class, or null when it is not one. */
    private static Map<?, ?> staticMap(Class<?> owner, String name) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(name);
        if (!Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType()))
            throw new NoSuchFieldException(owner.getName() + "." + name + " is not a static Map");
        f.setAccessible(true);
        return (Map<?, ?>) f.get(null);
    }

    /**
     * The class when it is already loaded by the game's class loader, else null (a class that never loaded holds nothing).
     * Outside ModLauncher (offline tools) the class is looked up without initialising it.
     */
    static Class<?> loaded(String name) throws ReflectiveOperationException {
        ClassLoader loader = ClosedWorldRelease.class.getClassLoader();
        if (LOADED_CLASS != null) return (Class<?>) LOADED_CLASS.invoke(loader, name);
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Method loadedClassMethod() {
        ClassLoader loader = ClosedWorldRelease.class.getClassLoader();
        if (loader == null || !loader.getClass().getName().equals("cpw.mods.modlauncher.TransformingClassLoader")) return null;
        try {
            Method m = loader.getClass().getMethod("getLoadedClass", String.class);
            return m.getReturnType() == Class.class ? m : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** For the offline proof: every part's owner class name, in release order. */
    public static List<String> owners() {
        return List.of(CITADEL_LINK);
    }
}
