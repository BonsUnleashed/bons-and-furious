package bons.furious.patch.closed_world_release;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch closed_world_reference_release (fix, since 1.0.49; both sides: the integrated server of a
 * single-player world and the client, a dedicated server too). Idea: Collections Of Optimizations (vanilla.releaseClosedWorld),
 * idea text only. No code of any target mod is carried: this class only reads and clears their fields by reflection and
 * calls their own public methods. SRG member names for Minecraft.
 *
 * What leaks. Some mods keep a reference to a world after it is closed, so the closed world (the ClientLevel with its
 * chunks, or the whole integrated server with every ServerLevel) stays in memory on the title screen and next to the next
 * world. Checked in each mod's current 1.20.1 build (bytecode of every reader and writer of the field):
 *  - Citadel 2.6.1 (2.6.3 the same): EntityLinkButton.renderedEntites, a static HashMap<String, Entity> that the button's
 *    renderWidget fills with putIfAbsent(id, type.create(Minecraft.level)) and never clears: the first preview entity of
 *    every entity link in a Citadel book (Alex's Mobs' and Alex's Caves' guide books) keeps the ClientLevel it was created
 *    in until the game is restarted.
 *  - Opposing Force 2.0.3: CommonProxy.SKYVERN_SPAWNER_MAP, a static HashMap<ServerLevel, SkyvernSpawner> its level tick
 *    listener fills (one entry per ServerLevel ever ticked) and never clears.
 *  - xaerolib 1.7.3 (inside Xaero's Minimap / World Map): every config channel's ServerConfigManager keeps the server in a
 *    field that only its own getServer() drops (when the server is stopped), and nothing calls getServer() between worlds.
 *  - Simply Swords 1.56.0: SoundHelper.soundSchedulers holds one ScheduledExecutorService per looping sound; the loop's task
 *    holds the entity and its ServerLevel and ends only when it finds the entity dead or without the effect. Entities of a
 *    stopped server are saved, not killed, so a loop that runs when the world closes keeps playing (playSound from its own
 *    thread, on the closed level) and keeps the world, for as long as the JVM runs.
 *
 * What the switch does, only when the mod's class is loaded (never loading a class early) and its field has the expected
 * static type (the guard fingerprints every method of these classes that reads or writes the field):
 *  - when the client closes its level (Forge's ForgeHooksClient.handleClientLevelClosing, leaving a world): Citadel's map is
 *    cleared. Its entries are only previews for book pages, re-created with the current level the next time a page draws
 *    them (renderWidget evaluates type.create every frame anyway; putIfAbsent then keeps the new one).
 *  - when a server is stopping (ServerLifecycleHooks.handleServerStopping, after the ServerStoppingEvent listeners): every
 *    Simply Swords loop is stopped exactly as its own stopLoopingSound does (removed from the map, then shutdownNow). Only one
 *    server runs at a time, so every loop belongs to it.
 *  - when a server has stopped (ServerLifecycleHooks.handleServerStopped, after every ServerStoppedEvent listener ran and
 *    before Forge clears its current-server field): Opposing Force's entries whose ServerLevel belongs to that server are
 *    removed (it creates a new spawner for every new level), and each xaerolib ServerConfigManager that still points at that
 *    server is asked for getServer() once, which drops the stopped server itself (exactly what its next call would do).
 * Nothing else changes: no other field is read or written, no event, packet or saved data differs.
 *
 * Left out on purpose (numbers in notes/closed_world_release.md): Citadel's AbstractPathJob.trackingMap (no class of Citadel or
 * of any other jar here or in extra_mods ever writes it: it is always empty); KubeJS UtilsJS.staticServer (2001.6.4-build.138,
 * 2001.6.5-build.16, -24 and -26 all set it to null in their own serverStopped); KubeJS ServerScriptManager.instance (releasing
 * it could turn later ScriptType.SERVER lookups into errors); Copycats and Subtle Effects (they clean up themselves).
 *
 * -Dbons_and_furious.closedWorldReferenceRelease=false keeps every reference as before.
 * -Dbons_and_furious.closedWorldReferenceRelease.shadow=true (verification runs only): nothing is released; every reference
 * the switch would release is counted in SHADOW_MISMATCHES (a reference to a closed world the mod did not drop itself) and
 * logged once per part, SHADOW_CHECKS counts the release passes.
 */
public final class ClosedWorldRelease {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.closedWorldReferenceRelease", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.closedWorldReferenceRelease.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** References released (in shadow mode: found) per part since the game started. */
    public static final AtomicLong CITADEL = new AtomicLong(), OPPOSING_FORCE = new AtomicLong(), XAEROLIB = new AtomicLong(),
            SIMPLY_SWORDS = new AtomicLong();

    static final String CITADEL_LINK = "com.github.alexthe666.citadel.client.gui.EntityLinkButton";
    static final String OF_PROXY = "com.unusualmodding.opposing_force.utils.CommonProxy";
    static final String XAERO_REGISTRY = "xaero.lib.common.config.channel.register.ConfigChannelRegistry";
    static final String SS_SOUND = "net.sweenus.simplyswords.util.SoundHelper";

    /** Parts that failed once (WARN logged): left alone until the game is restarted. */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final Set<String> ANNOUNCED = ConcurrentHashMap.newKeySet();
    /** ModLauncher's TransformingClassLoader.getLoadedClass(String) (public, exported); null in other class loaders. */
    private static final Method LOADED_CLASS = loadedClassMethod();

    private ClosedWorldRelease() {
    }

    /** ForgeHooksClient.handleClientLevelClosing, at its start (client thread): the client leaves its level. */
    public static void clientLevelClosing(Object level) {
        if (!enabled) return;
        pass();
        part(CITADEL_LINK, ClosedWorldRelease::citadelLinks);
    }

    /** ServerLifecycleHooks.handleServerStopping, at its end (server thread). */
    public static void serverStopping(MinecraftServer server) {
        if (!enabled) return;
        pass();
        part(SS_SOUND, ClosedWorldRelease::simplySwordsLoops);
    }

    /** ServerLifecycleHooks.handleServerStopped, after the ServerStoppedEvent listeners (server thread). */
    public static void serverStopped(MinecraftServer server) {
        if (!enabled) return;
        pass();
        part(OF_PROXY, () -> opposingForceSpawners(server));
        part(XAERO_REGISTRY, () -> xaerolibServers(server));
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

    // ---- Citadel: EntityLinkButton.renderedEntites -------------------------------------------------------------------

    private static int citadelLinks() throws ReflectiveOperationException {
        Class<?> c = loaded(CITADEL_LINK);
        if (c == null) return 0;                                   // no book page drew an entity link: nothing kept
        Map<?, ?> map = staticMap(c, "renderedEntites");
        if (map == null || map.isEmpty()) return 0;
        int n = map.size();
        if (!SHADOW) map.clear();
        CITADEL.addAndGet(n);
        return n;
    }

    // ---- Simply Swords: SoundHelper.soundSchedulers --------------------------------------------------------------------

    private static int simplySwordsLoops() throws ReflectiveOperationException {
        Class<?> c = loaded(SS_SOUND);
        if (c == null) return 0;                                   // no sound loop was ever started
        Map<?, ?> map = staticMap(c, "soundSchedulers");
        if (map == null || map.isEmpty()) return 0;
        int n = 0;
        for (Map.Entry<?, ?> e : new ArrayList<>(map.entrySet())) {
            if (!(e.getValue() instanceof ExecutorService scheduler)) continue;
            if (SHADOW) {
                n++;
                continue;
            }
            // stopLoopingSound's own steps for this sound id: remove the executor, then shutdownNow it
            if (map.remove(e.getKey(), scheduler)) {
                scheduler.shutdownNow();
                n++;
            }
        }
        SIMPLY_SWORDS.addAndGet(n);
        return n;
    }

    // ---- Opposing Force: CommonProxy.SKYVERN_SPAWNER_MAP ---------------------------------------------------------------

    private static int opposingForceSpawners(MinecraftServer server) throws ReflectiveOperationException {
        Class<?> c = loaded(OF_PROXY);
        if (c == null) return 0;
        Map<?, ?> map = staticMap(c, "SKYVERN_SPAWNER_MAP");
        if (map == null || map.isEmpty()) return 0;
        int n = 0;
        for (Iterator<?> it = map.keySet().iterator(); it.hasNext(); ) {
            Object key = it.next();
            if (key instanceof ServerLevel level && level.m_7654_() == server) {
                n++;
                if (!SHADOW) it.remove();
            }
        }
        OPPOSING_FORCE.addAndGet(n);
        return n;
    }

    // ---- xaerolib: ServerConfigManager.server -------------------------------------------------------------------------

    private static volatile Field xaeroChannels, xaeroServer;
    private static volatile Method xaeroManagerOf, xaeroGetServer;

    private static int xaerolibServers(MinecraftServer server) throws ReflectiveOperationException {
        Class<?> registryClass = loaded(XAERO_REGISTRY);
        if (registryClass == null) return 0;
        Object registry = registryClass.getField("INSTANCE").get(null);
        if (registry == null) return 0;
        if (xaeroChannels == null) {
            Field f = registryClass.getDeclaredField("channels");
            f.setAccessible(true);
            xaeroChannels = f;
        }
        Object channels = xaeroChannels.get(registry);
        if (!(channels instanceof Map<?, ?> byId) || byId.isEmpty()) return 0;
        int n = 0;
        for (Object channel : new ArrayList<>(byId.values())) {
            if (channel == null) continue;
            if (xaeroManagerOf == null) xaeroManagerOf = channel.getClass().getMethod("getServerConfigManager");
            Object manager = xaeroManagerOf.invoke(channel);
            if (manager == null) continue;
            if (xaeroServer == null) {
                Field f = manager.getClass().getDeclaredField("server");
                if (f.getType() != MinecraftServer.class || Modifier.isStatic(f.getModifiers()))
                    throw new NoSuchFieldException("ServerConfigManager.server is not an instance MinecraftServer field");
                f.setAccessible(true);
                xaeroGetServer = manager.getClass().getMethod("getServer");
                xaeroServer = f;
            }
            if (xaeroServer.get(manager) != server) continue;      // another server, or already dropped
            n++;
            if (SHADOW) continue;
            xaeroGetServer.invoke(manager);                         // its own release: the server is stopped -> null
            if (xaeroServer.get(manager) == server)
                LOGGER.warn("Bons and Furious: closed_world_reference_release: xaerolib's ServerConfigManager kept the stopped server (isStopped() was false)");
        }
        XAEROLIB.addAndGet(n);
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
        return List.of(CITADEL_LINK, SS_SOUND, OF_PROXY, XAERO_REGISTRY);
    }
}
