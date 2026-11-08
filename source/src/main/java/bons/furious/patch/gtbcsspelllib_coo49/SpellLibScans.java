package bons.furious.patch.gtbcsspelllib_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch gtbcs_spell_lib_shake_scan_presence_gate (GTBC's SpellLib 2.2.0-1.21.1 for NeoForge 1.21.1, All
 * Rights Reserved; client; exact). Ours; no SpellLib code is carried. Idea: Collections Of Optimizations
 * (gtbcs.cacheShakeScan), idea text only.
 *
 * What the library does: every frame ClientEvents.onCameraSetup asks the client level for every GSLScreenShakeEntity and
 * every GSLFollowingScreenShakeEntity within 50 blocks of the local player (two searches over the player's box grown by 50
 * in every direction: a walk over up to 7 x 7 x 7 entity sections each) and sums their shake; and on every GUI frame (the
 * crosshair layer) processScreenFlashEffects and processPowerInversionEffects make one such search each, for
 * GSLScreenFlashEntity and GSLPowerInversionEntity. Each listener only loops over its list (casting every element to the
 * searched class); an empty list leaves the camera and the overlay untouched.
 *
 * The switch rests on vanilla_entity_class_count_layer (registration bons_and_furious.gtbcsspelllib_coo49.entity_classes.json:
 * the four classes and their subclasses, counted while SpellLib is installed and this switch is on): while the client
 * level holds no instance of the searched class, the search is marked EMPTY: vanilla_entity_walk_skip leaves out its walk
 * over the entity sections, which could visit no such entity. The call itself still runs (Valkyrien Skies' box hooks, the
 * profiler's "getEntities" count, NeoForge's part-entity pass) and returns the new empty list it would have returned. Only
 * searches for exactly one of the four classes are marked; with an instance anywhere in the level, or counts the layer
 * is not sure of, the search runs in full. Runtime flag: -Dbons_and_furious.gtbcsSpellLibShakeScanPresenceGate=false.
 * SHADOW MODE (rigs): -Dbons_and_furious.gtbcsSpellLibShakeScanPresenceGate.shadow=true never marks; where the gate would act
 * it runs the search in full and checks that it found nothing (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class SpellLibScans {
    public static final String KEY = "gtbcs_spell_lib_shake_scan_presence_gate";
    static final String[] NAMES = {
            "com.gametechbc.spelllib.entity.GSLScreenShakeEntity",
            "com.gametechbc.spelllib.entity.GSLFollowingScreenShakeEntity",
            "com.gametechbc.spelllib.entity.GSLScreenFlashEntity",
            "com.gametechbc.spelllib.entity.GSLPowerInversionEntity"};

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.gtbcsSpellLibShakeScanPresenceGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.gtbcsSpellLibShakeScanPresenceGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (the render thread writes them): searches marked EMPTY. */
    public static long markedEmpty;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Per kind: the class the search names, remembered by identity after one check by name. */
    private static final Class<?>[] SEEN = new Class<?>[NAMES.length];
    private static volatile boolean announced;

    private SpellLibScans() {
    }

    /** The layer's indices, resolved on first use (the layer reads its registration files when it first initialises). */
    private static final class Indices {
        static final int[] OF = new int[NAMES.length];

        static {
            for (int i = 0; i < NAMES.length; i++) OF[i] = EntityPresenceLink.trackedIndex(NAMES[i]);
        }
    }

    /** Which of the four classes the search names, or -1. */
    private static int kind(Class<?> searched) {
        if (searched == null) return -1;
        for (int i = 0; i < NAMES.length; i++) if (SEEN[i] == searched) return i;
        String n = searched.getName();
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(n)) {
                SEEN[i] = searched;
                return i;
            }
        }
        return -1;
    }

    private static void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: {}: SpellLib's screen effect searches walk no entity sections while the client level holds none of the searched effect entities{}",
                KEY, SHADOW ? " (SHADOW MODE: the searches run in full and are compared)" : "");
    }

    /** One of the four ClientEvents searches (getEntitiesOfClass(Class, AABB) on the client level). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        if (!enabled || level == null) return original.call(level, searched, box);
        int which = kind(searched);
        if (which < 0) return original.call(level, searched, box);
        int index = Indices.OF[which];
        if (index < 0 || !EntityPresenceLink.none(level, index)) return original.call(level, searched, box);
        announce();
        if (SHADOW) {
            List found = original.call(level, searched, box);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e instanceof PartEntity) continue;   // the walk never yields part entities; NeoForge's pass after it does
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no {}, the search found {}", KEY, n, level, NAMES[which], e);
                break;
            }
            return found;
        }
        if (!EntityPresenceLink.markEmpty(KEY, level, searched, index)) return original.call(level, searched, box);
        markedEmpty++;
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }
}
