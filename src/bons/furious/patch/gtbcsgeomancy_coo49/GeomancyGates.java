package bons.furious.patch.gtbcsgeomancy_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group gtbcsgeomancy_coo49 (GTBC's Geomancy Plus 2.0.0, All Rights Reserved; needs Iron's Spellbooks).
 * Ours; no Geomancy Plus code is carried. Two switches:
 *
 * gtbcs_geomancy_caster_effect_precheck (exact result, both sides; kind change, off by default: against the jar's own
 * method the wrapper costs more than the skipped lookup on spell casting mobs and on entities without effects, and
 * gains only on entities with effects). Idea: Collections Of Optimizations
 * (gtbcsgeomancy.skipNonCasterCastingTick), idea text only. CastingHandler.onEntityCast runs on the living tick of every
 * living entity on both sides: it stores entity.hasEffect(CASTING_EFFECT) first and only then asks "instanceof
 * AbstractSpellCastingMob"; the stored answer is read only inside that branch (to cancel the mob's cast on a server level).
 * So for every entity that is not an Iron's spell casting mob the answer is never used, and GeomancyCastingPrecheckMixin
 * answers false without asking (LivingEntity.hasEffect is a read of the entity's effect map; the only class in our pack
 * or in the tested Iron's family that overrides it, fdlib's cutscene camera, answers from the client player with no side
 * effect). For spell casting mobs, and for a null entity (stock throws), the call runs as shipped. The effect's registry
 * object is still read first (the call's argument), as shipped. Flag: -Dbons_and_furious.gtbcsGeomancyCasterEffectPrecheck=false.
 *
 * gtbcs_geomancy_grandmaster_spawn_presence_gate (exact, server levels). Idea: Collections Of Optimizations
 * (gtbcsgeomancy.gateGrandmasterSpawnScan), idea text only. GeoGrandmasterEntity.checkGrandmasterSpawnRules (the
 * grandmaster's spawn placement rule) asks, for a sky-visible position in daytime, for every GeoGrandmasterEntity within
 * 64 blocks (ServerLevel.getEntitiesOfClass over a 129-block cube: a walk over up to 9 x 9 x 9 entity sections) and
 * refuses the spawn when the list is not empty. While vanilla_entity_class_count_layer says the level holds no
 * GeoGrandmasterEntity (registration bons_and_furious.gtbcsgeomancy_coo49.entity_classes.json: the class and its
 * subclasses, counted while Geomancy Plus is installed and this switch is on), the search is marked EMPTY:
 * vanilla_entity_walk_skip leaves out its walk over the entity sections, which could visit no grandmaster. The call itself
 * still runs (the profiler's "getEntities" count, Forge's part-entity pass, every other mod's hook on the search) and
 * returns the new empty list it would have returned; the rest of the rule runs unchanged. Only searches for exactly
 * GeoGrandmasterEntity.class are marked; the gate answers "unknown" (the original runs) whenever the layer does (counts
 * doubtful, another thread, the layer off). Flag: -Dbons_and_furious.gtbcsGeomancyGrandmasterSpawnPresenceGate=false.
 * SHADOW MODE (rigs): -Dbons_and_furious.gtbcsGeomancyGrandmasterSpawnPresenceGate.shadow=true never marks; where the gate
 * would act it runs the search in full and checks that it found no grandmaster (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN
 * for the first 20).
 */
public final class GeomancyGates {
    public static final String PRECHECK_KEY = "gtbcs_geomancy_caster_effect_precheck";
    public static final String GATE_KEY = "gtbcs_geomancy_grandmaster_spawn_presence_gate";
    public static final String GRANDMASTER = "com.gametechbc.gtbcs_geomancy_plus.entity.mobs.geo_grandmaster.GeoGrandmasterEntity";

    public static volatile boolean precheck = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.gtbcsGeomancyCasterEffectPrecheck", "true"));
    public static volatile boolean gate = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.gtbcsGeomancyGrandmasterSpawnPresenceGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.gtbcsGeomancyGrandmasterSpawnPresenceGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields): grandmaster searches marked EMPTY. */
    public static long markedEmpty;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;
    /** The class the search names, remembered by identity after one check by name. */
    private static volatile Class<?> seen;

    private GeomancyGates() {
    }

    /** The layer's index of GeoGrandmasterEntity, resolved on first use (the layer reads its registration files when it initialises). */
    private static final class Index {
        static final int GRANDMASTER_INDEX = EntityPresenceLink.trackedIndex(GRANDMASTER);
    }

    /** True when the hasEffect answer is read: an Iron's spell casting mob (or null, where stock throws). */
    public static boolean caster(LivingEntity entity) {
        if (entity == null || entity instanceof AbstractSpellCastingMob) return true;
        if ((announced & 1) == 0) announce(0, PRECHECK_KEY + " applies (Geomancy Plus' casting tick asks entities that are not spell casting mobs for no effect)");
        return false;
    }

    private static void announce(int bit, String what) {
        synchronized (GeomancyGates.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}", what);
    }

    private static boolean names(Class<?> searched) {
        if (searched == null) return false;
        if (searched == seen) return true;
        if (!GRANDMASTER.equals(searched.getName())) return false;
        seen = searched;
        return true;
    }

    /** GeomancyGrandmasterSpawnGateMixin: the spawn rule's grandmaster search, marked EMPTY while the level holds none. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List grandmasterSearch(Level level, Class<?> searched, AABB box, Operation<List> original) {
        if (!gate || level == null || !names(searched)) return original.call(level, searched, box);
        int index = Index.GRANDMASTER_INDEX;
        if (index < 0) return original.call(level, searched, box);
        if (SHADOW) {
            if (!EntityPresenceLink.none(level, index)) return original.call(level, searched, box);
            announce(1, GATE_KEY + ": SHADOW MODE: the grandmaster spawn searches run in full and are checked");
            List found = original.call(level, searched, box);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e instanceof PartEntity) continue;
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no grandmaster, the spawn search found {}", GATE_KEY, n, level, e);
                break;
            }
            return found;
        }
        if (!EntityPresenceLink.markEmpty(GATE_KEY, level, searched, index)) return original.call(level, searched, box);
        markedEmpty++;
        if ((announced & 4) == 0) announce(2, GATE_KEY + ": the grandmaster spawn rule's search walks no entity sections while the level holds no grandmaster");
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }
}
