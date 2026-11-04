package bons.furious.patch.goety_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 presence gates of group goety_coo49 (Goety 2.5.60.0; 2.5.58.4 has the same methods). Helper of
 * the group's mixins; Goety is MIT, but no code of it is carried: the gates are our own logic.
 *
 *   goety_shake_scan_presence_gate (client)  ClientEvents.onSetupCamera asks the level every frame for every CameraShake
 *                                             within 20 blocks of the player and sums their shake;
 *   goety_wight_scan_presence_gate (both)    Wight.findWight(Entity, Predicate) asks it for every Wight within 64 blocks
 *                                             (callers: the fog events, the HUD Dread overlay and the client tick, several
 *                                             times per frame, and the Wight itself).
 * vanilla_entity_class_count_layer counts CameraShake and Wight (and subclasses) in every level's entity sections
 * (registration bons_and_furious.goety_coo49.entity_classes.json). While the level holds none, the search is made exactly
 * as before (Valkyrien Skies' box hooks, the profiler's "getEntities" count, Forge's part entity pass all run) but marked
 * EMPTY, so vanilla_entity_walk_skip leaves out its walk over the entity sections, which would visit no entity of that
 * class: the same empty list (a part entity is never a CameraShake or a Wight), so the camera angles, the fog and
 * findWight's null answer stay as before. With an instance anywhere in the level (or counts the layer is not sure of)
 * everything runs in full. Without the two layers (reached by name through EntityPresenceLink) every search runs in full.
 * While an instance exists, the search stands aside on the count read straight from the level's counts array (cached
 * by level, EntityPresenceLink.held: asking none() and all its checks on every such search put the
 * CameraShake search beside 10 pigs at 0.975 of the shipped call); a count of 0 still goes through none() before a mark.
 * Runtime flags (=false: off): bons_and_furious.goetyShakeScanPresenceGate, bons_and_furious.goetyWightScanPresenceGate.
 * SHADOW MODE: -Dbons_and_furious.goetyGates.shadow=true never marks; where a gate would act it runs the search in full
 * and counts SHADOW_CHECKS / SHADOW_MISMATCHES (a result holding any non-part entity; WARN for the first 20).
 */
public final class GoetyGates {
    public static final int CAMERA_SHAKE = 0, WIGHT = 1;
    static final String[] NAMES = {"com.Polarice3.Goety.common.entities.util.CameraShake", "com.Polarice3.Goety.common.entities.hostile.Wight"};
    static final String[] KEYS = {"goety_shake_scan_presence_gate", "goety_wight_scan_presence_gate"};

    public static volatile boolean shake = flag("goetyShakeScanPresenceGate");
    public static volatile boolean wight = flag("goetyWightScanPresenceGate");
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.goetyGates.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics (plain fields): searches marked EMPTY. */
    public static long marked;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Class<?>[] SEEN = new Class<?>[NAMES.length];
    private static volatile int announced;

    private GoetyGates() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The layer's indices, resolved on first use (the layer reads its registration files when it first initialises). */
    private static final class Indices {
        static final int[] OF = new int[NAMES.length];

        static {
            for (int i = 0; i < NAMES.length; i++) OF[i] = EntityPresenceLink.trackedIndex(NAMES[i]);
        }
    }

    /** The search names exactly the class of kind {@code which} (by name, remembered by identity). */
    private static boolean names(int which, Class<?> searched) {
        if (searched == null) return false;
        if (SEEN[which] == searched) return true;
        if (!searched.getName().equals(NAMES[which])) return false;
        SEEN[which] = searched;
        return true;
    }

    private static void announce(int which) {
        if ((announced & (1 << which)) != 0) return;
        synchronized (GoetyGates.class) {
            if ((announced & (1 << which)) != 0) return;
            announced |= 1 << which;
        }
        LOGGER.info("Bons and Furious: {}: the search walks no entity sections while the level holds no {}{}", KEYS[which],
                NAMES[which].substring(NAMES[which].lastIndexOf('.') + 1), SHADOW ? " (SHADOW MODE: the search runs in full and is compared)" : "");
    }

    @SuppressWarnings("rawtypes")
    private static List shadow(int which, List found, Object level) {
        SHADOW_CHECKS.incrementAndGet();
        for (Object e : found) {
            if (e instanceof PartEntity) continue;   // the walk never yields part entities; Forge's pass after it does
            if (SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no {}, the search found {}", KEYS[which],
                        SHADOW_MISMATCHES.get(), level, NAMES[which], e);
            }
            break;
        }
        return found;
    }

    private static boolean none(int which, Object level) {
        return level instanceof Level l && EntityPresenceLink.none(l, Indices.OF[which]);
    }

    /** getEntitiesOfClass(Class, AABB) whose class is the tracked one of kind {@code which} (onSetupCamera). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(boolean on, int which, Object level, Class<?> searched, Object box, Operation<List> original) {
        if (!on || !names(which, searched) || EntityPresenceLink.held(level, Indices.OF[which]) || !none(which, level)) return original.call(level, searched, box);
        announce(which);
        if (SHADOW) return shadow(which, original.call(level, searched, box), level);
        if (!EntityPresenceLink.markEmpty(KEYS[which], level, searched, Indices.OF[which])) return original.call(level, searched, box);
        marked++;
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** getEntitiesOfClass(Class, AABB, Predicate) whose class is the tracked one of kind {@code which} (findWight). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(boolean on, int which, Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!on || !names(which, searched) || EntityPresenceLink.held(level, Indices.OF[which]) || !none(which, level)) return original.call(level, searched, box, predicate);
        announce(which);
        if (SHADOW) return shadow(which, original.call(level, searched, box, predicate), level);
        if (!EntityPresenceLink.markEmpty(KEYS[which], level, searched, Indices.OF[which])) return original.call(level, searched, box, predicate);
        marked++;
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            EntityPresenceLink.unmark();
        }
    }
}
