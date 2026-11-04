package bons.furious.patch.goetyawaken_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 presence gates of group goetyawaken_coo49 (Goety Awaken 1.3.9.4; client). Helper of the group's
 * mixins; no code of Goety Awaken is carried (LGPL-2.1 code; the gates are our own logic).
 *
 * <p>goetyawaken_scan_presence_gates: Goety Awaken's ClientEvents ask the client level for entities within 64 blocks of the
 * player four times per frame: NamelessOne in onRenderFog (every RenderFog event) and onGetFogColor (every ComputeFogColor
 * event), SorcererServant and BoundSorcerer in onRenderLevelStage (AFTER_TRANSLUCENT_BLOCKS, their spell beams).
 * vanilla_entity_class_count_layer counts the three classes (and subclasses) in the client level's entity sections
 * (registration bons_and_furious.goetyawaken_coo49.entity_classes.json); while the level holds none of the searched class,
 * the search is made exactly as before (the profiler's "getEntities" count, Forge's part entity pass) but marked EMPTY, so
 * vanilla_entity_walk_skip leaves out its walk, which would visit no entity of that class: the same empty list, so the fog,
 * its colour and the beams stay as before (each listener acts only inside its loop). With an instance in the level, or
 * counts the layer is not sure of, the search runs in full. Without the layers (reached by name) every search runs in full.
 * Runtime flag (=false: off): bons_and_furious.goetyawakenScanPresenceGates. SHADOW MODE:
 * -Dbons_and_furious.goetyawakenGates.shadow=true never marks; where a gate would act it runs the search in full and counts
 * SHADOW_CHECKS / SHADOW_MISMATCHES (a result holding any non-part entity; WARN for the first 20).
 */
public final class GaGates {
    public static final int NAMELESS_ONE = 0, SORCERER_SERVANT = 1, BOUND_SORCERER = 2;
    static final String[] NAMES = {"com.k1sak1.goetyawaken.common.entities.hostile.undead.necromancer.NamelessOne",
            "com.k1sak1.goetyawaken.common.entities.ally.illager.SorcererServant", "com.k1sak1.goetyawaken.common.entities.ally.undead.BoundSorcerer"};
    static final String KEY = "goetyawaken_scan_presence_gates";

    public static volatile boolean gates = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.goetyawakenScanPresenceGates", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.goetyawakenGates.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics (plain field): searches marked EMPTY. */
    public static long marked;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Class<?>[] SEEN = new Class<?>[NAMES.length];
    private static volatile int announced;

    private GaGates() {
    }

    /** The layer's indices, resolved on first use (the layer reads its registration files when it first initialises). */
    private static final class Indices {
        static final int[] OF = new int[NAMES.length];

        static {
            for (int i = 0; i < NAMES.length; i++) OF[i] = EntityPresenceLink.trackedIndex(NAMES[i]);
        }
    }

    /**
     * The kind of the searched class (exact name, remembered by identity), -1 for any other class. Every remembered class is
     * tried by identity before any name is compared (the third kind used to compare two names on every call).
     */
    private static int kindOf(Class<?> searched) {
        if (searched == null) return -1;
        Class<?>[] seen = SEEN;
        for (int i = 0; i < seen.length; i++) if (seen[i] == searched) return i;
        String name = searched.getName();
        for (int i = 0; i < NAMES.length; i++) {
            if (name.equals(NAMES[i])) {
                seen[i] = searched;
                return i;
            }
        }
        return -1;
    }

    private static void announce(int which) {
        if ((announced & (1 << which)) != 0) return;
        synchronized (GaGates.class) {
            if ((announced & (1 << which)) != 0) return;
            announced |= 1 << which;
        }
        LOGGER.info("Bons and Furious: {}: the search walks no entity sections while the client level holds no {}{}", KEY,
                NAMES[which].substring(NAMES[which].lastIndexOf('.') + 1), SHADOW ? " (SHADOW MODE: the search runs in full and is compared)" : "");
    }

    @SuppressWarnings("rawtypes")
    private static List shadow(int which, List found, Object level) {
        SHADOW_CHECKS.incrementAndGet();
        for (Object e : found) {
            if (e instanceof PartEntity) continue;
            if (SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no {}, the search found {}", KEY, SHADOW_MISMATCHES.get(),
                        level, NAMES[which], e);
            }
            break;
        }
        return found;
    }

    /**
     * The tracked kind whose search may be marked EMPTY (the flag on, the searched class exactly one of the three, the layer
     * certain the client level holds none), or -1: the caller runs the search as shipped. The handlers make that call
     * themselves: the helper's one Operation call site would serve four wrapped call sites, and a call site that sees
     * several Operation classes keeps its argument array and a virtual call when the JIT does not inline the helper.
     */
    public static int markableKind(Object level, Class<?> searched) {
        int which;
        if (!gates || (which = kindOf(searched)) < 0 || !(level instanceof Level l) || !EntityPresenceLink.none(l, Indices.OF[which])) return -1;
        return which;
    }

    /** The search of kind {@code which} while the level holds none: marked EMPTY (or compared in shadow mode), then run. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scanMarked(int which, Object level, Class<?> searched, Object box, Operation<List> original) {
        announce(which);
        if (SHADOW) return shadow(which, original.call(level, searched, box), level);
        if (!EntityPresenceLink.markEmpty(KEY, level, searched, Indices.OF[which])) return original.call(level, searched, box);
        marked++;
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** What every handler does, in one call: ClientLevel.getEntitiesOfClass(Class, AABB) in the three listeners. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(Object level, Class<?> searched, Object box, Operation<List> original) {
        int which = markableKind(level, searched);
        return which < 0 ? original.call(level, searched, box) : scanMarked(which, level, searched, box, original);
    }
}
