package bons.furious.patch.goal_presence_gates;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49: group goal_presence_gates, one goal-presence design for every caller below, both sides (goals
 * run where the mob's AI runs: the server). Helper of the group's mixins; no Mojang or target-mod code here.
 *
 * vanilla_goal_class_presence_gate (the layer of this group): AvoidEntityGoal.canUse searches the goal's avoided class in a
 * box around the mob every other tick, and NearestAttackableTargetGoal.findTarget searches the goal's target class (for
 * every non-player target type). For a goal that one of the caller keys below VOUCHED for when it was created (the goal's
 * class is registered with vanilla_entity_class_count_layer), while the mob's level holds no instance of that class at all,
 * the goal's own search is marked EMPTY (vanilla_entity_walk_skip) for that one call: the search runs as before (Valkyrien
 * Skies' box hooks, the profiler's "getEntities" count, Forge's part pass) but its section walk, which could find nothing,
 * is left out; canUse / findTarget then run unchanged on the same empty list (toAvoid = null, false; target = null). Every
 * other goal, and every call while an instance exists anywhere in the level (or counts the layer is unsure of), runs
 * exactly as before. The goal's RNG draw (NearestAttackableTargetGoal.canUse's randomInterval) happens before findTarget
 * and is untouched.
 *
 * Caller keys (each vouches only for the goals its own target creates, so an updated mod steps aside alone):
 *   alexsmobs_creeper_avoid_presence_gate   Alex's Mobs 1.22.9 ServerEvents.onEntityFinalizeSpawn: every creeper's
 *                                           AvoidEntityGoal for EntitySnowLeopard and EntityTiger (COO alexsmobs.skipCreeperAvoidGoals)
 *   alexsmobs_spider_fly_presence_gate      the same method: every spider's NearestAttackableTargetGoal for EntityFly
 *                                           (randomInterval 1: a search every goal tick; COO alexsmobs.spiderFlyScanInterval)
 *   dummmmmmy_scarecrow_presence_gate       dummmmmmy 2.0.12 ModEvents.onEntityJoinWorld: the scarecrow AvoidEntityGoal of
 *                                           animals and the decoy NearestAttackableTargetGoal of monsters, TargetDummyEntity
 *                                           (COO dummmmmmy.scarecrowScanInterval)
 *   primitivemobs_avoid_presence_gate       Primitive Mobs Revived 1.0.4: the villagers' AvoidEntityGoal for Goblin
 *                                           (PrimitiveMobsEvents.onEntityJoin) and the sheepmen's for ZombifiedPiglin
 *                                           (Sheepman.registerGoals) (COO primitivemobs.villagerGoblinAvoidInterval,
 *                                           .sheepmanPiglinAvoidInterval)
 * A vouch is a number kept in the goal (the layer's index of its class plus one); a goal nobody vouched for keeps 0.
 * Also here (not goals, the same EMPTY mark at a mod's own search): crittersandcompanions_koi_luck_presence_gate (Critters
 * and Companions 2.3.5) and crittersandcompanions271_koi_luck_presence_gate (2.7.1, new package): PlayerHandler.onPlayerTick
 * searches KoiFishEntity in the player's box +-10 every server tick and gives Luck when it finds three (COO
 * crittersandcompanions.koiLuckScanInterval); with no koi in the level the search's walk is left out.
 * Runtime flags: -Dbons_and_furious.goalClassPresenceGate=false (the layer of this group) and one per caller key (read when
 * the goal is created): .alexsmobsCreeperAvoidPresenceGate, .alexsmobsSpiderFlyPresenceGate,
 * .dummmmmmyScarecrowPresenceGate, .primitivemobsAvoidPresenceGate; for the koi sites (read per call)
 * .crittersandcompanionsKoiLuckPresenceGate, .crittersandcompanions271KoiLuckPresenceGate.
 * SHADOW MODE: -Dbons_and_furious.goalClassPresenceGate.shadow=true never marks; where it would, the original runs and is
 * checked (an avoid goal must answer false with nothing to avoid, a target goal must find no target); SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20.
 */
public final class GoalPresenceGates {
    public static volatile boolean enabled = flag("goalClassPresenceGate");
    public static volatile boolean alexsmobsCreeperAvoid = flag("alexsmobsCreeperAvoidPresenceGate");
    public static volatile boolean alexsmobsSpiderFly = flag("alexsmobsSpiderFlyPresenceGate");
    public static volatile boolean dummmmmmyScarecrow = flag("dummmmmmyScarecrowPresenceGate");
    public static volatile boolean primitivemobsAvoid = flag("primitivemobsAvoidPresenceGate");
    /** Critters and Companions' koi luck scan (PlayerHandler.onPlayerTick): 2.3.5 and 2.7.1, one key per build. */
    public static volatile boolean crittersandcompanionsKoiLuck = flag("crittersandcompanionsKoiLuckPresenceGate");
    public static volatile boolean crittersandcompanions271KoiLuck = flag("crittersandcompanions271KoiLuckPresenceGate");
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.goalClassPresenceGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields; the goals' thread writes them): goals vouched for, calls marked. */
    public static long vouched, marked;

    public static final String AVOID_KEY = "vanilla_goal_class_presence_gate";
    /** The classes the caller keys vouch for (exact names; the vouch mixins compare the goal's class with them). */
    public static final String SNOW_LEOPARD = "com.github.alexthe666.alexsmobs.entity.EntitySnowLeopard",
            TIGER = "com.github.alexthe666.alexsmobs.entity.EntityTiger", FLY = "com.github.alexthe666.alexsmobs.entity.EntityFly",
            DUMMY = "net.mehvahdjukaar.dummmmmmy.common.TargetDummyEntity", GOBLIN = "com.misanthropy.primitive_mobs.entity.monster.Goblin",
            ZOMBIFIED_PIGLIN = "net.minecraft.world.entity.monster.ZombifiedPiglin";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private GoalPresenceGates() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** AvoidEntityGoal / NearestAttackableTargetGoal (this group's mixins): the vouch, index + 1 of the goal's class, 0 = none. */
    public interface Vouched {
        int bons$presence();

        void bons$presence(int vouch);
    }

    /**
     * A caller key's goal source made {@code goal} with {@code type} as its avoided / target class: vouch for it when the
     * caller's flag is on, this group's layer key applied to the goal's class (it implements Vouched) and the class is
     * counted by vanilla_entity_class_count_layer (registered and its key enabled). Returns the goal unchanged.
     */
    public static <G> G vouch(boolean callerOn, G goal, Class<?> type) {
        if (!callerOn || !(goal instanceof Vouched v) || type == null || type.isInterface() || PartEntity.class.isAssignableFrom(type)) return goal;
        int index = EntityPresenceLink.trackedIndex(type.getName());
        if (index >= 0) {
            v.bons$presence(index + 1);
            vouched++;
        }
        return goal;
    }

    /** The goal's own search can only come back empty: the layer says the level holds no instance of its class. */
    private static boolean none(Level level, int vouch) {
        return vouch > 0 && level != null && EntityPresenceLink.none(level, vouch - 1);
    }

    private static void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: {}: vouched avoid / target goals skip the section walk of their search while their level holds none of their class{}",
                AVOID_KEY, SHADOW ? " (SHADOW MODE: the original runs every time and is checked)" : "");
    }

    /**
     * AvoidEntityGoal.canUse (WrapMethod): for a vouched goal whose class the level does not hold, the original runs with its
     * search marked EMPTY.
     */
    public static boolean avoidCanUse(int vouch, Level level, Class<?> avoidClass, Supplier<LivingEntity> toAvoid, Operation<Boolean> original) {
        if (!enabled || !none(level, vouch)) return original.call();
        announce();
        if (SHADOW) {
            boolean result = original.call();
            SHADOW_CHECKS.incrementAndGet();
            LivingEntity avoided = toAvoid.get();
            if ((result || avoided != null) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no {}, the avoid goal answered {} avoiding {}", AVOID_KEY,
                        SHADOW_MISMATCHES.get(), level, avoidClass.getName(), result, avoided);
            return result;
        }
        if (!EntityPresenceLink.markEmpty(AVOID_KEY, level, avoidClass, vouch - 1)) return original.call();
        marked++;
        try {
            return original.call();
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /**
     * NearestAttackableTargetGoal.findTarget (WrapMethod): for a vouched goal whose target class the level does not hold, the
     * original runs with its search marked EMPTY.
     */
    public static void findTarget(int vouch, Level level, Class<?> targetType, Supplier<LivingEntity> target, Operation<Void> original) {
        if (!enabled || !none(level, vouch)) {
            original.call();
            return;
        }
        announce();
        if (SHADOW) {
            original.call();
            SHADOW_CHECKS.incrementAndGet();
            LivingEntity found = target.get();
            if (found != null && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no {}, the target goal found {}", AVOID_KEY,
                        SHADOW_MISMATCHES.get(), level, targetType.getName(), found);
            return;
        }
        if (!EntityPresenceLink.markEmpty(AVOID_KEY, level, targetType, vouch - 1)) {
            original.call();
            return;
        }
        marked++;
        try {
            original.call();
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /**
     * A caller site's own search, getEntitiesOfClass(searched, box, predicate) (Critters and Companions' koi luck scan): while
     * the level holds no instance of the searched class (registered with the layer under its own name), the search is marked
     * EMPTY; returns the original's list. An EMPTY mark is exact for whatever registered class the search names (the walk of
     * a search for a class the level does not hold visits no entity), so the site needs no class check of its own.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List siteScan(boolean on, String key, Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!on || searched == null || !(level instanceof Level l)) return original.call(level, searched, box, predicate);
        int index = SITE_INDEX.get(searched);
        if (index < 0 || !EntityPresenceLink.none(l, index)) return original.call(level, searched, box, predicate);
        if (SHADOW) {
            List found = original.call(level, searched, box, predicate);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e instanceof PartEntity) continue;
                if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no {}, the search found {}", key, SHADOW_MISMATCHES.get(), level,
                            searched.getName(), e);
                break;
            }
            return found;
        }
        if (!EntityPresenceLink.markEmpty(key, level, searched, index)) return original.call(level, searched, box, predicate);
        marked++;
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** The layer's index of a searched class by its own name (-1 when not registered in this JVM). */
    private static final ClassValue<Integer> SITE_INDEX = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            return EntityPresenceLink.trackedIndex(type.getName());
        }
    };
}
