package bons.furious.patch.shake_gates_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch cnc_wechuge_fog_scan_presence_gate (group shake_gates_c49; client; Minecraft 1.21.1 +
 * NeoForge 21.1). Helper of CncFogColorGateMixin and CncFogDistanceGateMixin; no code of Critters n' Crawlers or of
 * Minecraft is carried.
 *
 * Critters n' Crawlers 2.2.5 FogColorSetupProcedure.execute (ViewportEvent.ComputeFogColor, every frame) and
 * FogDistanceSetupProcedure.execute (ViewportEvent.RenderFog, terrain fog) each search WechugeEntity in a 24 and then a 12
 * block box around the camera entity, and only test each result with isEmpty. vanilla_entity_class_count_layer counts
 * WechugeEntity in every level's entity sections (registration bons_and_furious.shake_gates_c49.entity_classes.json); while
 * the level holds none, the searches are made exactly as before (the profiler's "getEntities" count, NeoForge's part pass,
 * every other mod's hook on the search) but marked EMPTY, so vanilla_entity_walk_skip leaves out their walk over the entity
 * sections, which could visit no Wechuge. The procedures then run on the same empty lists (same fog). With a Wechuge
 * anywhere in the level (or counts the layer is not sure of) everything runs in full.
 *
 * Requires vanilla_entity_class_count_layer and vanilla_entity_walk_skip (EntityClassCounts and EntityWalks, reached through
 * method handles found by name so this class compiles on its own); without them every search runs in full.
 * Runtime flag: -Dbons_and_furious.cncWechugeFogScanPresenceGate=false runs every search in full.
 * SHADOW MODE: -Dbons_and_furious.shakeGates49.shadow=true never marks; where the gate would act the search runs and is
 * checked (no non-part entity in its list); SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20.
 */
public final class CncWechugeFogGate {
    public static final String KEY = "cnc_wechuge_fog_scan_presence_gate";
    public static final String WECHUGE_ENTITY = "net.imasillylittleguy.cnc.entity.WechugeEntity";

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cncWechugeFogScanPresenceGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.shakeGates49.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics (the render thread writes it): searches marked. */
    public static long marked;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final MethodHandle TRACKED_INDEX, NONE, MARK_EMPTY, UNMARK;
    /** The class the searches name (checked by name once, then by identity). */
    private static volatile Class<?> seen;
    private static volatile boolean announced;

    static {
        MethodHandle ti = null, none = null, me = null, um = null;
        MethodHandles.Lookup l = MethodHandles.lookup();
        ClassLoader loader = CncWechugeFogGate.class.getClassLoader();
        try {
            Class<?> c = Class.forName("bons.furious.patch.crittersandcompanions_c2.EntityClassCounts", false, loader);
            ti = l.findStatic(c, "trackedIndex", MethodType.methodType(int.class, String.class));
            none = l.findStatic(c, "none", MethodType.methodType(boolean.class, Level.class, int.class));
            Class<?> w = Class.forName("bons.furious.patch.client_shake_gate.EntityWalks", false, loader);
            me = l.findStatic(w, "markEmpty", MethodType.methodType(boolean.class, String.class, Object.class, Class.class, int.class));
            um = l.findStatic(w, "unmark", MethodType.methodType(void.class));
        } catch (Throwable t) {
            ti = null;
            none = null;
            me = null;
            um = null;
            LOGGER.warn("Bons and Furious: cannot reach vanilla_entity_class_count_layer / vanilla_entity_walk_skip ({}); {} runs every search in full", t.toString(), KEY);
        }
        TRACKED_INDEX = ti;
        NONE = none;
        MARK_EMPTY = me;
        UNMARK = um;
    }

    private CncWechugeFogGate() {
    }

    /** The layer's index of WechugeEntity, resolved on first use (the layer reads its registration files when it initialises). */
    private static final class Index {
        static final int WECHUGE = trackedIndex(WECHUGE_ENTITY);
    }

    private static int trackedIndex(String name) {
        try {
            return TRACKED_INDEX == null ? -1 : (int) TRACKED_INDEX.invokeExact(name);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** The layer says the level holds no WechugeEntity (false = there is one, or the layer is not sure). */
    private static boolean none(Object level, int index) {
        if (NONE == null || index < 0 || !(level instanceof Level l)) return false;
        try {
            return (boolean) NONE.invokeExact(l, index);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean markEmpty(Object level, Class<?> searched, int index) {
        if (MARK_EMPTY == null || UNMARK == null) return false;
        try {
            return (boolean) MARK_EMPTY.invokeExact(KEY, level, searched, index);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void unmark() {
        try {
            if (UNMARK != null) UNMARK.invokeExact();
        } catch (Throwable t) {
            // nothing marked
        }
    }

    /** The search names exactly WechugeEntity (by name, remembered by identity). */
    private static boolean names(Class<?> searched) {
        if (searched == null) return false;
        if (seen == searched) return true;
        if (!searched.getName().equals(WECHUGE_ENTITY)) return false;
        seen = searched;
        return true;
    }

    /** getEntitiesOfClass(WechugeEntity, AABB, e -> true) of the two fog procedures: marked EMPTY while the level holds none. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        int index = Index.WECHUGE;
        if (!enabled || !names(searched) || !none(level, index)) return original.call(level, searched, box, predicate);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {}: the search walks no entity sections while the client level holds no WechugeEntity{}", KEY,
                    SHADOW ? " (SHADOW MODE: the search runs in full and is compared)" : "");
        }
        if (SHADOW) {
            List found = original.call(level, searched, box, predicate);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e instanceof PartEntity) continue;   // the walk never yields part entities; NeoForge's pass after it does
                if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no {}, the search found {}", KEY, SHADOW_MISMATCHES.get(),
                            level, WECHUGE_ENTITY, e);
                break;
            }
            return found;
        }
        if (!markEmpty(level, searched, index)) return original.call(level, searched, box, predicate);
        marked++;
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            unmark();
        }
    }
}
