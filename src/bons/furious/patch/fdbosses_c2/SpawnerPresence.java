package bons.furious.patch.fdbosses_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch fdbosses_spawner_presence_gate (FD Bosses 3.1.0.3 on Minecraft 1.20.1, both sides). Helper of
 * the fdbosses_c2 gate mixins; no FD Bosses or Mojang code here.
 *
 * FD Bosses searches for its boss spawners around every broken or placed block and every explosion (+-100 blocks: 13-14
 * entity-section columns, past Radium's small-box shortcut, so the full vanilla walk) and around every player whose drops
 * it may move (+-50); its client looks for a Malkuth spawner every tick at night (+-30), and its collision hook looks for
 * Chesed kinetic fields around every moving player and thrown ender pearl (+-20). While the level holds no entity of the
 * searched class at all (vanilla_entity_class_count_layer counts them where the search looks), such a search can only
 * return an empty list, so the gate returns a new empty list after counting the profiler's "getEntities" line, as the
 * search does; the calling FD Bosses method then runs unchanged on that list (an explosion still walks its affected blocks,
 * the sky still fades, no shape is added). With such an entity anywhere in the level, or the count not known for sure, the
 * original search runs.
 *
 * Requires vanilla_entity_class_count_layer (group crittersandcompanions_c2). The layer is reached through a constant
 * method handle to its public static EntityClassCounts.noneOf(Level, Class), looked up by name, so this group compiles on
 * its own (the release jar holds both); with that switch off the layer answers "unknown" and the original runs.
 * -Dbons_and_furious.fdbossesSpawnerPresenceGate=false turns the gate off at runtime.
 * SHADOW MODE for rigs: -Dbons_and_furious.fdbossesSpawnerPresenceGate.shadow=true runs the original search whenever the
 * gate would answer, returns the original's list and counts SHADOW_CHECKS / SHADOW_MISMATCHES (a non-empty original;
 * WARN for the first 20).
 */
public final class SpawnerPresence {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fdbossesSpawnerPresenceGate", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.fdbossesSpawnerPresenceGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Searches answered without walking (statistics for probes; plain field, written by the level's thread). */
    public static long skipped;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** EntityClassCounts.noneOf(Level, Class) of vanilla_entity_class_count_layer, or null when that class is absent. */
    private static final MethodHandle NONE_OF = layer();
    private static volatile boolean announced;

    private SpawnerPresence() {
    }

    private static MethodHandle layer() {
        try {
            Class<?> counts = Class.forName("bons.furious.patch.crittersandcompanions_c2.EntityClassCounts", false, SpawnerPresence.class.getClassLoader());
            return MethodHandles.lookup().findStatic(counts, "noneOf", MethodType.methodType(boolean.class, Level.class, Class.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: fdbosses_spawner_presence_gate cannot reach vanilla_entity_class_count_layer ({}); FD Bosses' searches run unchanged", e.toString());
            return null;
        }
    }

    /** True when a search of class {@code searched} in {@code level} is certain to find nothing (the gate may answer). */
    static boolean nothingThere(Object level, Class<?> searched) {
        if (!enabled || NONE_OF == null || !(level instanceof Level l)) return false;
        try {
            if (!(boolean) NONE_OF.invokeExact(l, (Class<?>) searched)) return false;
        } catch (Throwable t) {
            return false;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fdbosses_spawner_presence_gate: FD Bosses' spawner and kinetic-field searches are skipped while the level has none{}",
                    SHADOW ? " (SHADOW MODE: the original runs every time and is compared)" : "");
        }
        return true;
    }

    /** getEntitiesOfClass(Class, AABB) call sites. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List search(Object level, Class<?> searched, Object box, Operation<List> original) {
        if (nothingThere(level, searched)) {
            if (SHADOW) return shadow(original.call(level, searched, box), level, searched);
            ((Level) level).m_46473_().m_6174_("getEntities");
            skipped++;
            return new ArrayList();
        }
        return original.call(level, searched, box);
    }

    /** getEntitiesOfClass(Class, AABB, Predicate) call sites. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List search(Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (nothingThere(level, searched)) {
            if (SHADOW) return shadow(original.call(level, searched, box, predicate), level, searched);
            ((Level) level).m_46473_().m_6174_("getEntities");
            skipped++;
            return new ArrayList();
        }
        return original.call(level, searched, box, predicate);
    }

    @SuppressWarnings("rawtypes")
    private static List shadow(List found, Object level, Class<?> searched) {
        SHADOW_CHECKS.incrementAndGet();
        if (!found.isEmpty() && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: fdbosses_spawner_presence_gate SHADOW MISMATCH {}: the gate would answer an empty list for {} in {}, the original "
                    + "found {}", SHADOW_MISMATCHES.get(), searched.getName(), level, found);
        }
        return found;
    }
}
