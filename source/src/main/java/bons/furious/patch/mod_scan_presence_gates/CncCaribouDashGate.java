package bons.furious.patch.mod_scan_presence_gates;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch cnc_caribou_dash_presence_gate (group mod_scan_presence_gates; both sides; Minecraft 1.21.1 +
 * NeoForge 21.1). Helper of CncCaribouDashGateMixin; no code of Critters n' Crawlers or of Minecraft is carried.
 *
 * Critters n' Crawlers 2.2.5 CaribouDashDamageProcedure (an EntityTickEvent.Pre handler: every entity, every tick, both
 * sides) searches CaribouEntity in a 0.5 block box around the entity, sorts the list by distance and lets the nearest one
 * hurt and push the entity when it is dashing. vanilla_entity_class_count_layer counts CaribouEntity (registration
 * bons_and_furious.mod_scan_presence_gates.entity_classes.json; subclasses count with their class); while the level holds
 * none, the search is answered the way it ends when it finds nothing, without the call: the profiler's "getEntities" count
 * and a new empty ArrayList. Exact: the walk could visit no entity; NeoForge's part pass cannot add one (a part entity is
 * never a CaribouEntity). With a caribou anywhere in the level (or counts the layer is not sure of) the search runs in full.
 * Answering is cheaper here than marking the walk (the box is so small that the walk itself costs little).
 *
 * Requires vanilla_entity_class_count_layer (EntityClassCounts, reached through method handles found by name so this
 * class compiles on its own); without it every search runs in full.
 * Runtime flag: -Dbons_and_furious.cncCaribouDashPresenceGate=false runs every search in full.
 * SHADOW MODE: -Dbons_and_furious.modScanGates.shadow=true never answers; where the gate would act the search runs and is
 * checked (no non-part entity in its list); SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20.
 */
public final class CncCaribouDashGate {
    public static final String KEY = "cnc_caribou_dash_presence_gate";
    public static final String CARIBOU_ENTITY = "net.imasillylittleguy.cnc.entity.CaribouEntity";

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cncCaribouDashPresenceGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.modScanGates.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain field): caribou searches answered without the call. */
    public static long answered;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final MethodHandle TRACKED_INDEX, NONE;
    /** The class the search names (checked by name once, then by identity). */
    private static volatile Class<?> seen;
    private static volatile boolean announced;

    static {
        MethodHandle ti = null, none = null;
        try {
            Class<?> c = Class.forName("bons.furious.patch.crittersandcompanions_c2.EntityClassCounts", false, CncCaribouDashGate.class.getClassLoader());
            MethodHandles.Lookup l = MethodHandles.lookup();
            ti = l.findStatic(c, "trackedIndex", MethodType.methodType(int.class, String.class));
            none = l.findStatic(c, "none", MethodType.methodType(boolean.class, Level.class, int.class));
        } catch (Throwable t) {
            ti = null;
            none = null;
            LOGGER.warn("Bons and Furious: cannot reach vanilla_entity_class_count_layer ({}); {} runs every search in full", t.toString(), KEY);
        }
        TRACKED_INDEX = ti;
        NONE = none;
    }

    private CncCaribouDashGate() {
    }

    /** The layer's index of CaribouEntity, resolved on first use (the layer reads its registration files when it initialises). */
    private static final class Index {
        static final int CARIBOU = trackedIndex(CARIBOU_ENTITY);
    }

    private static int trackedIndex(String name) {
        try {
            return TRACKED_INDEX == null ? -1 : (int) TRACKED_INDEX.invokeExact(name);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** The level holds no CaribouEntity (false = there is one, or the layer is not sure). */
    private static boolean none(Level level) {
        int index = Index.CARIBOU;
        if (NONE == null || index < 0) return false;
        try {
            return (boolean) NONE.invokeExact(level, index);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The search names exactly CaribouEntity (by name, remembered by identity). */
    private static boolean names(Class<?> searched) {
        if (searched == null) return false;
        if (seen == searched) return true;
        if (!searched.getName().equals(CARIBOU_ENTITY)) return false;
        seen = searched;
        return true;
    }

    /**
     * The caribou search, getEntitiesOfClass(CaribouEntity, a 0.5 block box, e -> true), made for every entity every tick:
     * while the level holds no CaribouEntity it is answered the way the search ends when it finds nothing, without the call.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!enabled || !names(searched) || !(level instanceof Level l) || !none(l)) return original.call(level, searched, box, predicate);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {}: the search is answered without walking while the level holds no CaribouEntity{}", KEY,
                    SHADOW ? " (SHADOW MODE: the search runs in full and is checked)" : "");
        }
        if (SHADOW) {
            List found = original.call(level, searched, box, predicate);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e instanceof PartEntity) continue;   // the walk never yields part entities; NeoForge's pass after it does
                if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no {}, the search found {}", KEY, SHADOW_MISMATCHES.get(),
                            level, CARIBOU_ENTITY, e);
                break;
            }
            return found;
        }
        answered++;
        l.getProfiler().incrementCounter("getEntities");
        return new ArrayList();
    }
}
