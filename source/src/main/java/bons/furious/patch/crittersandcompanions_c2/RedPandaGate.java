package bons.furious.patch.crittersandcompanions_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch crittersandcompanions_red_panda_gate (Critters and Companions 2.7.0, Minecraft 1.21.1 + NeoForge
 * 21.1.252, server side in practice: goals run where the mob's AI runs). Helper of AvoidEntityGoalRedPandaMixin; no C&C or
 * Mojang code here.
 *
 * Critters and Companions gives bees, endermen, iron golems, llamas, polar bears, spiders, vexes and wolves an
 * AvoidEntityGoal for its RedPandaEntity (NeutralMobsMixin). Goal selection asks canUse every other tick, and each canUse
 * searches a 33 x 7 x 33 box through every entity section it overlaps for a red panda. When the mob's level holds no red
 * panda at all (vanilla_entity_class_count_layer counts them where the search looks), that search can only come back empty,
 * and canUse with an empty search does exactly this: the level's profiler counts one "getEntities", toAvoid becomes null,
 * the answer is false (getNearestEntity of an empty list never tests its conditions). The gate does those three things
 * without the search. With a red panda anywhere in the level (or the count not known for sure) canUse runs unchanged.
 * Requires vanilla_entity_class_count_layer; with that switch off this one leaves every goal unchanged.
 * -Dbons_and_furious.crittersAndCompanionsRedPandaGate=false turns it off at runtime.
 * SHADOW MODE for rigs: -Dbons_and_furious.crittersAndCompanionsRedPandaGate.shadow=true runs the original canUse every
 * time the gate would answer, compares (answer false, toAvoid null) and keeps the original's result; SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20 mismatches.
 * Ported to 1.21.1: C&C 2.7.0's NeutralMobsMixin (io.github.bonsaistudi0s.crittersandcompanions.common.mixin) adds the same
 * goal to the same eight mobs at registerGoals HEAD (avoidClass RedPandaEntity, 16 blocks, 2.0 / 1.5, alert-and-tame
 * predicate); the entity class moved to io.github.bonsaistudi0s.crittersandcompanions.common.entity.RedPandaEntity, which
 * EntityClassCounts now tracks. Vanilla canUse and getNearestEntity(List, ...) are unchanged.
 */
public final class RedPandaGate {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.crittersAndCompanionsRedPandaGate", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.crittersAndCompanionsRedPandaGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** canUse calls answered without the search (statistics for probes; plain field, the goals' thread writes it). */
    public static long skipped;
    public static final byte RED_PANDA_GOAL = 1, OTHER_GOAL = 2;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private RedPandaGate() {
    }

    /** Whether an AvoidEntityGoal's avoided class is Critters and Companions 2.7.0's RedPandaEntity (by exact name). */
    public static byte kind(Class<?> avoidClass) {
        return EntityClassCounts.index(avoidClass) == EntityClassCounts.RED_PANDA ? RED_PANDA_GOAL : OTHER_GOAL;
    }

    /** True when the red-panda search of a goal in this level can only come back empty (the gate then answers). */
    public static boolean nothingToAvoid(Level level) {
        if (!EntityClassCounts.none(level, EntityClassCounts.RED_PANDA)) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: crittersandcompanions_red_panda_gate: red-panda avoid goals skip their search while their level has no red panda{}",
                    SHADOW ? " (SHADOW MODE: the original runs every time and is compared)" : "");
        }
        return true;
    }

    /** Shadow mode: run the original canUse where the gate would have answered, compare, keep the original's result. */
    public static boolean shadow(Operation<Boolean> original, Supplier<Object> toAvoid, Level level) {
        boolean result = original.call();
        SHADOW_CHECKS.incrementAndGet();
        Object avoided = toAvoid.get();
        if ((result || avoided != null) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: crittersandcompanions_red_panda_gate SHADOW MISMATCH {}: the gate would answer false with nothing to avoid, "
                    + "the original answered {} avoiding {} in {}", SHADOW_MISMATCHES.get(), result, avoided, level);
        }
        return result;
    }
}
