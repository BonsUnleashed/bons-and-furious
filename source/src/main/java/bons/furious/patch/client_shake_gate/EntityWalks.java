package bons.furious.patch.client_shake_gate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_entity_walk_skip, since 1.0.49: the mark API other groups use (both sides). Helper of
 * LevelWalkSkipMixin; no Minecraft code is carried.
 *
 * Level.getEntities(EntityTypeTest, AABB, Predicate, List, int) (Minecraft 1.21.1, the end of every getEntitiesOfClass) counts
 * the profiler's "getEntities", walks the level's entity sections, then runs NeoForge's part-entity pass. A caller that knows the
 * walk of ONE of its searches can only come back empty, or that the search's result is not used, marks that search on its
 * own thread right before the call and clears the mark in a finally block; LevelWalkSkipMixin then leaves out that one
 * search's walk. The call itself still runs (the profiler count, NeoForge's part pass, every other mod's hook on these
 * methods), so nothing outside the left-out walk changes.
 *
 *   EMPTY  {@link #markEmpty}: the walk is left out only while vanilla_entity_class_count_layer still says the level holds
 *          no instance of tracked entry {@code index} (asked again at the walk, by the thread that owns the level). For a
 *          search whose base class is that entry (or a subclass of it) the left-out walk would have visited no entity; a
 *          caller whose base class is wider (Mob for an interface entry) must show that its predicate is false for every
 *          other entity and has no side effect (alexscaves_shake_scan_presence_gate's argument).
 *   UNUSED {@link #markUnused}: the walk is left out unconditionally. The caller must show that the search's result is
 *          never read and that its predicate (called by the walk for every entity of the base class in the box) has no side
 *          effect; NeoForge's part pass still calls it for part entities, as before.
 *
 * A mark belongs to the thread that placed it (a ThreadLocal), names the level and the searched base class, and is used by
 * the first walk on that thread for that level and base class (normally the marked search's own: between the mark and its
 * walk only the getEntitiesOfClass chain runs). A search of another class or level, and any search on another thread, walks
 * as before. One mark per thread at a time (a nested mark is refused, its search runs in full). With
 * -Dbons_and_furious.entityWalkSkip=false nothing is marked. SHADOW MODE: -Dbons_and_furious.entityWalkMarks.shadow=true
 * never leaves a walk out; SHADOW_CHECKS counts the walks that would have been left out (callers compare their own results
 * in their shadow modes; the EMPTY premise itself is checked by the layer's shadow mode).
 *
 * Callers reach these static methods through method handles found by name (their groups compile on their own), as the
 * EntityPresenceLink classes of groups shake_gates_c49 and goal_presence_gates do.
 */
public final class EntityWalks {
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.entityWalkMarks.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields, racy across threads like ShakeGate's): marks placed, walks left out per kind. */
    public static long marked, leftOutEmpty, leftOutUnused;
    public static final int EMPTY = 1, UNUSED = 2;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String LAYER = "bons.furious.patch.crittersandcompanions_c2.EntityClassCounts";
    /** EntityClassCounts.none(Level, int), or null when the layer's class is absent. */
    private static final MethodHandle NONE;
    /** Live marks of all threads: 0 lets every walk skip the ThreadLocal read. */
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final ThreadLocal<Mark> MARK = ThreadLocal.withInitial(Mark::new);
    private static final Map<String, Boolean> ANNOUNCED = new ConcurrentHashMap<>();

    static {
        MethodHandle none = null;
        try {
            Class<?> counts = Class.forName(LAYER, false, EntityWalks.class.getClassLoader());
            none = MethodHandles.lookup().findStatic(counts, "none", MethodType.methodType(boolean.class, Level.class, int.class));
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_entity_walk_skip cannot reach vanilla_entity_class_count_layer ({}); EMPTY marks are refused", t.toString());
        }
        NONE = none;
    }

    /** One thread's mark; only that thread reads or writes it. */
    private static final class Mark {
        boolean live;
        int kind, index;
        Object level;
        Class<?> base;
    }

    private EntityWalks() {
    }

    private static boolean none(Object level, int index) {
        if (NONE == null || index < 0 || !(level instanceof Level l)) return false;
        try {
            return (boolean) NONE.invokeExact(l, index);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean place(String key, int kind, Object level, Class<?> base, int index, String what) {
        Mark m = MARK.get();
        if (m.live) return false;   // nested: the inner search runs in full
        m.kind = kind;
        m.level = level;
        m.base = base;
        m.index = index;
        m.live = true;
        ACTIVE.incrementAndGet();
        marked++;
        if (key != null && ANNOUNCED.putIfAbsent(key, Boolean.TRUE) == null) {
            LOGGER.info("Bons and Furious: {}: {}{}", key, what, SHADOW ? " (SHADOW MODE: entityWalkMarks never leaves a walk out)" : "");
        }
        return true;
    }

    /**
     * Marks the next search for {@code base} in {@code level} on this thread as certain to walk past no match: placed only
     * when vanilla_entity_walk_skip is on and vanilla_entity_class_count_layer says (to this thread, which then owns the
     * level's entities) that the level holds no instance of tracked entry {@code index}; asked again at the walk. True when
     * placed; the caller must call {@link #unmark()} in a finally block either way (harmless when nothing was placed).
     * {@code key} names the caller's switch in the one INFO line written when it first marks.
     */
    public static boolean markEmpty(String key, Object level, Class<?> base, int index) {
        if (!ShakeGate.walkSkip || base == null || !none(level, index)) return false;
        return place(key, EMPTY, level, base, index, "its search walks no entity sections while the level holds none of the searched class");
    }

    /**
     * Marks the next search for {@code base} in {@code level} on this thread as one whose result is not used (the caller's
     * proof: nothing reads the list, the predicate has no side effect). Placed only when vanilla_entity_walk_skip is on.
     * True when placed; the caller must call {@link #unmark()} in a finally block either way.
     */
    public static boolean markUnused(String key, Object level, Class<?> base) {
        if (!ShakeGate.walkSkip || base == null || !(level instanceof Level)) return false;
        return place(key, UNUSED, level, base, -1, "its search walks no entity sections when its result is not used");
    }

    /** Ends this thread's mark (if any is still live). */
    public static void unmark() {
        Mark m = MARK.get();
        if (!m.live) return;
        m.live = false;
        m.level = null;
        m.base = null;
        ACTIVE.decrementAndGet();
    }

    /**
     * LevelWalkSkipMixin, before the walk of Level.getEntities: true (leave the walk out) only for the search this thread
     * marked, in that level, for that base class; an EMPTY mark only while the layer still says none. The mark is used once.
     */
    public static boolean leaveOut(Object level, EntityTypeTest<?, ?> test) {
        if (ACTIVE.get() == 0) return false;
        Mark m = MARK.get();
        if (!m.live || m.level != level || test.getBaseClass() != m.base) return false;
        int kind = m.kind, index = m.index;
        m.live = false;
        m.level = null;
        m.base = null;
        ACTIVE.decrementAndGet();
        if (!ShakeGate.walkSkip) return false;
        if (kind == EMPTY && !none(level, index)) return false;
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            return false;
        }
        if (kind == EMPTY) leftOutEmpty++;
        else leftOutUnused++;
        return true;
    }
}
