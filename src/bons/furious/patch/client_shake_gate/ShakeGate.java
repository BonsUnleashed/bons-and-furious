package bons.furious.patch.client_shake_gate;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious camera-shake presence gates (Minecraft 1.20.1 + Forge 47.4.16, client). Helper of the client_shake_gate
 * mixins; no code of the target mods or of Minecraft is carried.
 *
 * Five mods look for their screen-shake entities once per frame, from their ViewportEvent.ComputeCameraAngles listener, on
 * the render thread: Cataclysm 3.16 (ScreenShake_Entity, +-20 blocks around the player), Alex's Caves 2.0.2 (every Mob that
 * implements ShakesScreen, +-64 around the camera entity), Legendary Monsters 2.2.2 (CameraShakeEntity +-20, and in third
 * person DynamicCameraZoomEntity +-20), Mowzie's Mobs 1.7.3 (EntityCameraShake +-20) and Cryptic Foes 1.0.4
 * (EntityCameraShake +-100). Each search walks every entity section its box overlaps. vanilla_entity_class_count_layer counts
 * those classes (and ShakesScreen implementors) in every level's entity sections; while the client level holds none, the
 * walk of such a search can only come back empty.
 *
 * What changes: when a gate's key is on and the layer says the level holds none, the gate makes the mod's search exactly as
 * before (Level.getEntitiesOfClass: Valkyrien Skies' box hooks, the profiler's "getEntities" count, Forge's part-entity pass
 * all run), but marks it, and vanilla_entity_walk_skip leaves out that one search's walk over the entity sections (the
 * walk that finds nothing). For the class gates the walk visits no entity at all (no section holds one). For Alex's Caves the
 * search is for Mob with the predicate "instanceof ShakesScreen", and the walk visits the level's other mobs: for each it
 * runs that predicate (a type test, false for all of them) and Ad Astra 1.15.20's part hook (which adds parts only of its
 * MultipartEntity vehicles, never Mobs), so it adds nothing and changes nothing either. The listener then runs unchanged on
 * the same empty list: same camera angles, same random draws (Alex's Caves draws only while something shakes), same zoom.
 * A shake entity anywhere in the level (or counts the layer is not sure of) and the search runs in full.
 *
 * Requires vanilla_entity_class_count_layer (group crittersandcompanions_c2, reached through constant method handles, so this
 * group compiles on its own) and vanilla_entity_walk_skip (this group); with either off, every search runs in full.
 * Runtime flags (-D...=false turns one off): bons_and_furious.cataclysmShakeScanPresenceGate,
 * .alexscavesShakeScanPresenceGate, .legendaryMonstersShakeScanPresenceGate, .mowziesmobsShakeScanPresenceGate,
 * .crypticfoesShakeScanPresenceGate, .entityWalkSkip (vanilla_entity_walk_skip).
 * SHADOW MODE for rigs: -Dbons_and_furious.shakeScanPresenceGate.shadow=true never marks a search; where a gate would, it runs
 * the search in full and counts SHADOW_CHECKS / SHADOW_MISMATCHES (an entity that lives in an entity section in the result,
 * i.e. anything but a Forge part entity; WARN for the first 20).
 */
public final class ShakeGate {
    public static volatile boolean cataclysm = flag("cataclysmShakeScanPresenceGate");
    public static volatile boolean alexscaves = flag("alexscavesShakeScanPresenceGate");
    public static volatile boolean legendaryMonsters = flag("legendaryMonstersShakeScanPresenceGate");
    public static volatile boolean mowziesmobs = flag("mowziesmobsShakeScanPresenceGate");
    public static volatile boolean crypticfoes = flag("crypticfoesShakeScanPresenceGate");
    /** vanilla_entity_walk_skip's runtime switch. */
    public static volatile boolean walkSkip = flag("entityWalkSkip");
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.shakeScanPresenceGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Searches marked by a gate, and walks left out by vanilla_entity_walk_skip (statistics; the render thread writes them). */
    public static long marked, walksSkipped;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String LAYER = "bons.furious.patch.crittersandcompanions_c2.EntityClassCounts";
    private static final String SHAKES_SCREEN_NAME = "com.github.alexmodguy.alexscaves.server.entity.util.ShakesScreen";
    /** EntityClassCounts.noneOf(Level, Class) and none(Level, int), or null when the layer's class is absent. */
    private static final MethodHandle NONE_OF, NONE;
    /** EntityClassCounts' index of Alex's Caves' ShakesScreen, or -1. */
    private static final int SHAKES_SCREEN;
    private static volatile int announced;   // one bit per key: its INFO line was written

    /*
     * The marked search: written and read only by the thread that owns the level's entity storage (the layer answers "none"
     * to no other thread). Other threads read these plain fields in skipWalk but never find their own thread in
     * markedThread, so a stale value cannot make them skip anything.
     */
    private static Thread markedThread;
    private static Object markedLevel;
    private static Class<?> markedBase;
    private static int markedIndex;          // -1: re-check with noneOf(level, markedBase); else none(level, markedIndex)

    static {
        MethodHandle noneOf = null, none = null;
        int shakes = -1;
        try {
            Class<?> counts = Class.forName(LAYER, false, ShakeGate.class.getClassLoader());
            MethodHandles.Lookup l = MethodHandles.lookup();
            noneOf = l.findStatic(counts, "noneOf", MethodType.methodType(boolean.class, Level.class, Class.class));
            none = l.findStatic(counts, "none", MethodType.methodType(boolean.class, Level.class, int.class));
            shakes = (int) l.findStatic(counts, "trackedIndex", MethodType.methodType(int.class, String.class)).invokeExact(SHAKES_SCREEN_NAME);
        } catch (Throwable t) {
            noneOf = none = null;
            LOGGER.warn("Bons and Furious: the camera-shake presence gates cannot reach vanilla_entity_class_count_layer ({}); every search runs in full", t.toString());
        }
        NONE_OF = noneOf;
        NONE = none;
        SHAKES_SCREEN = shakes;
    }

    private ShakeGate() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    static boolean noneOf(Object level, Class<?> searched) {
        if (NONE_OF == null || !(level instanceof Level l)) return false;
        try {
            return (boolean) NONE_OF.invokeExact(l, (Class<?>) searched);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean none(Object level, int index) {
        if (NONE == null || index < 0 || !(level instanceof Level l)) return false;
        try {
            return (boolean) NONE.invokeExact(l, index);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------- the gates

    /** getEntitiesOfClass(Class, AABB) in a listener whose search class is the tracked shake class itself. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(boolean on, int key, Object level, Class<?> searched, Object box, Operation<List> original) {
        if (!on || !noneOf(level, searched)) return original.call(level, searched, box);
        announce(key);
        if (SHADOW) return shadow(original.call(level, searched, box), level, searched);
        mark(level, searched, -1);
        try {
            return original.call(level, searched, box);
        } finally {
            unmark();
        }
    }

    /** Alex's Caves: getEntitiesOfClass(Mob.class, AABB, mob -> mob instanceof ShakesScreen). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scanShakesScreen(Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!alexscaves || searched != Mob.class || !none(level, SHAKES_SCREEN)) return original.call(level, searched, box, predicate);
        announce(KEY_ALEXSCAVES);
        if (SHADOW) return shadow(original.call(level, searched, box, predicate), level, searched);
        mark(level, Mob.class, SHAKES_SCREEN);
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            unmark();
        }
    }

    public static final int KEY_CATACLYSM = 0, KEY_ALEXSCAVES = 1, KEY_LEGENDARY_MONSTERS = 2, KEY_MOWZIESMOBS = 3, KEY_CRYPTICFOES = 4;
    private static final String[] KEYS = {"cataclysm_shake_scan_presence_gate", "alexscaves_shake_scan_presence_gate",
            "legendary_monsters_shake_scan_presence_gate", "mowziesmobs_shake_scan_presence_gate", "crypticfoes_shake_scan_presence_gate"};

    private static void announce(int key) {
        int a = announced;
        if ((a & (1 << key)) != 0) return;
        synchronized (ShakeGate.class) {
            if ((announced & (1 << key)) != 0) return;
            announced |= 1 << key;
        }
        LOGGER.info("Bons and Furious: {}: the camera-shake search walks no entity sections while the client level holds no shake entity{}",
                KEYS[key], SHADOW ? " (SHADOW MODE: the search runs in full and is compared)" : "");
    }

    private static void mark(Object level, Class<?> base, int index) {
        markedLevel = level;
        markedBase = base;
        markedIndex = index;
        markedThread = Thread.currentThread();
        marked++;
    }

    private static void unmark() {
        markedThread = null;
        markedLevel = null;
        markedBase = null;
    }

    @SuppressWarnings("rawtypes")
    private static List shadow(List found, Object level, Class<?> searched) {
        SHADOW_CHECKS.incrementAndGet();
        for (Object e : found) {
            if (e instanceof PartEntity) continue;   // the walk never yields part entities; Forge's pass after it does
            if (SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: camera-shake presence gate SHADOW MISMATCH {}: the layer says {} holds no {}, the search found {}",
                        SHADOW_MISMATCHES.get(), level, searched.getName(), e);
            }
            break;
        }
        return found;
    }

    // ------------------------------------------------------------------------------------------------- vanilla_entity_walk_skip

    /**
     * Level.getEntities(EntityTypeTest, AABB, Predicate, List, int), before its walk over the entity sections: true (leave the
     * walk out) only for the search a gate marked on this thread, in that level, for that base class, and only while the
     * layer still says the level holds none. The mark is used once.
     */
    public static boolean skipWalk(Object level, EntityTypeTest<?, ?> test) {
        if (markedThread != Thread.currentThread() || !walkSkip) return false;
        if (markedLevel != level || test.m_142225_() != markedBase) return false;
        int index = markedIndex;
        Class<?> base = markedBase;
        unmark();
        if (!(index < 0 ? noneOf(level, base) : none(level, index))) return false;
        walksSkipped++;
        return true;
    }
}
