package bons.furious.patch.witherreincarnated_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch witherreincarnated_wither_scan_presence_gate (Wither Reincarnated 1.0.5, All Rights Reserved;
 * both sides; exact). Ours; no Wither Reincarnated code is carried. Idea: Collections Of Optimizations
 * (witherreincarnated.skipIdleWitherScans), idea text only.
 *
 * What the mod does. (1) Server: every mob that is not undead and not tagged "not scared of wither" gets an avoid goal
 * (ModifyAIEvent$5, an AvoidEntityGoal over WitherBoss with a 64-block reach) whose canUse runs every other tick and starts
 * with level.getEntitiesOfClass(WitherBoss.class, its box grown by 64 in every direction, any): a walk over every entity
 * section of a 129-block cube around the mob. The list then goes to getNearestEntity; an empty list ends canUse with
 * "false" (toAvoid null). (2) Client: HandleWitherMusicEvent.tickMusic, every client tick (START phase, music volume above
 * 0), clears its map and walks ClientLevel.entitiesForRendering() (every entity of the client level) for WitherBoss
 * instances, the only ones it acts on.
 *
 * The switch, both gates resting on vanilla_entity_class_count_layer (registration
 * bons_and_furious.witherreincarnated_coo49.entity_classes.json: WitherBoss and its subclasses, counted while Wither
 * Reincarnated is installed and this switch is on):
 *  - server (AvoidWitherScanMixin): while the layer says the level holds no WitherBoss, the search is marked EMPTY:
 *    vanilla_entity_walk_skip leaves out its walk over the entity sections, which could visit no WitherBoss. The call
 *    still runs (the profiler's "getEntities" count, Forge's part pass, every other mod's hook on the search) and returns
 *    the new empty list it would have returned; the rest of canUse runs unchanged. Only searches for exactly
 *    WitherBoss.class are marked.
 *  - client (WitherMusicScanMixin): while the layer says the client level holds no WitherBoss, the loop gets an empty
 *    list instead of every entity of the level. The client level's entity list (its EntityLookup) only holds entities
 *    that are also in its entity sections (TransientEntitySectionManager adds an entity to both and removes it from both),
 *    so it holds no WitherBoss either and the loop would have acted on nothing.
 * Either gate answers "unknown" (the original runs) whenever the layer does: counts doubtful, another thread, the layer
 * off. Runtime flag: -Dbons_and_furious.witherreincarnatedWitherScanPresenceGate=false. SHADOW MODE (rigs):
 * -Dbons_and_furious.witherreincarnatedWitherScanPresenceGate.shadow=true never marks or skips; where a gate would act it
 * runs the original and checks that it found no WitherBoss (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class WrWitherScans {
    public static final String KEY = "witherreincarnated_wither_scan_presence_gate";
    public static final String WITHER = "net.minecraft.world.entity.boss.wither.WitherBoss";

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.witherreincarnatedWitherScanPresenceGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.witherreincarnatedWitherScanPresenceGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields): avoid searches marked EMPTY, music walks left out. */
    public static long markedEmpty, musicSkipped;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;

    private WrWitherScans() {
    }

    /** The layer's index of WitherBoss, resolved on first use (the layer reads its registration files when it initialises). */
    private static final class Index {
        static final int WITHER_INDEX = EntityPresenceLink.trackedIndex(WITHER);
    }

    private static void announce(int bit, String what) {
        if ((announced & (1 << bit)) != 0) return;
        synchronized (WrWitherScans.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}: {}", KEY, what);
    }

    private static void mismatch(String what) {
        long n = SHADOW_MISMATCHES.incrementAndGet();
        if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: {}", KEY, n, what);
    }

    /** AvoidWitherScanMixin: the avoid goal's WitherBoss search, marked EMPTY while the level holds no WitherBoss. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List avoidSearch(Level level, Class<?> searched, AABB box, Predicate predicate, Operation<List> original) {
        if (!enabled || searched != WitherBoss.class || level == null) return original.call(level, searched, box, predicate);
        int index = Index.WITHER_INDEX;
        if (index < 0) return original.call(level, searched, box, predicate);
        if (SHADOW) {
            if (!EntityPresenceLink.none(level, index)) return original.call(level, searched, box, predicate);
            announce(0, "SHADOW MODE: the avoid-wither searches run in full and are checked");
            List found = original.call(level, searched, box, predicate);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e instanceof PartEntity) continue;
                mismatch("the layer says " + level + " holds no wither, the avoid search found " + e);
                break;
            }
            return found;
        }
        if (!EntityPresenceLink.markEmpty(KEY, level, searched, index)) return original.call(level, searched, box, predicate);
        markedEmpty++;
        announce(1, "the mobs' avoid-wither searches walk no entity sections while the level holds no wither");
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** WitherMusicScanMixin: the music loop's entity list, empty while the client level holds no WitherBoss. */
    public static Iterable<Entity> musicEntities(Level level, Operation<Iterable<Entity>> original) {
        if (!enabled || level == null) return original.call(level);
        int index = Index.WITHER_INDEX;
        if (index < 0 || !EntityPresenceLink.none(level, index)) return original.call(level);
        if (SHADOW) {
            announce(2, "SHADOW MODE: the wither music loop walks every entity and is checked");
            Iterable<Entity> all = original.call(level);
            SHADOW_CHECKS.incrementAndGet();
            for (Entity e : all) {
                if (e instanceof WitherBoss) {
                    mismatch("the layer says the client level holds no wither, its entity list holds " + e);
                    break;
                }
            }
            return all;
        }
        musicSkipped++;
        announce(3, "the wither music loop walks no entities while the client level holds no wither");
        return List.of();
    }
}
