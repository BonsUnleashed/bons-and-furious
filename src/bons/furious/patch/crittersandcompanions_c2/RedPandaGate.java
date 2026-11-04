package bons.furious.patch.crittersandcompanions_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch crittersandcompanions_red_panda_gate (Critters and Companions 2.3.5 and, since 1.0.49, 2.7.1;
 * Minecraft 1.20.1, server side in practice: goals run where the mob's AI runs). Helper of AvoidEntityGoalRedPandaMixin; no
 * C&C or Mojang code here.
 *
 * Critters and Companions gives bees, endermen, iron golems, llamas, polar bears, spiders, vexes and wolves (and zombified
 * piglins) an AvoidEntityGoal for its RedPandaEntity (NeutralMobsMixin, ZombifiedPiglinMixin; 2.3.5 in package
 * com.github.eterdelta, 2.7.1 in io.github.bonsaistudi0s, the same goals). Goal selection asks canUse every other tick, and
 * each canUse searches a 33 x 7 x 33 box through every entity section it overlaps for a red panda. When the mob's level
 * holds no red panda at all (vanilla_entity_class_count_layer counts them where the search looks), that search can only come
 * back empty, and canUse with an empty search does exactly this: the level's profiler counts one "getEntities", toAvoid
 * becomes null, the answer is false (getNearestEntity of an empty list never tests its conditions). The gate does those
 * three things without the search. With a red panda anywhere in the level (or the count not known for sure) canUse runs
 * unchanged. The 2.3.5 class is a built-in entry of the layer; the 2.7.1 class is registered by this group's
 * bons_and_furious.crittersandcompanions_c2.entity_classes.json (since 1.0.49), so the gate no longer goes dead when C&C is
 * updated. Requires vanilla_entity_class_count_layer; with that switch off this one leaves every goal unchanged.
 * -Dbons_and_furious.crittersAndCompanionsRedPandaGate=false turns it off at runtime.
 * SHADOW MODE for rigs: -Dbons_and_furious.crittersAndCompanionsRedPandaGate.shadow=true runs the original canUse every
 * time the gate would answer, compares (answer false, toAvoid null) and keeps the original's result; SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20 mismatches.
 */
public final class RedPandaGate {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.crittersAndCompanionsRedPandaGate", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.crittersAndCompanionsRedPandaGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** canUse calls answered without the search (statistics for probes; plain field, the goals' thread writes it). */
    public static long skipped;
    /** Goal kinds (decided once per goal): 2.3.5 red panda, any other goal, since 1.0.49 the 2.7.1 red panda. */
    public static final byte RED_PANDA_GOAL = 1, OTHER_GOAL = 2, RED_PANDA_271_GOAL = 3;
    /** Critters and Companions 2.7.1's red panda (registered by this group's entity_classes resource). */
    public static final String RED_PANDA_271 = "io.github.bonsaistudi0s.crittersandcompanions.common.entity.RedPandaEntity";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private RedPandaGate() {
    }

    /** The layer's index of the 2.7.1 red panda (-1 when not counted), looked up once, at the first goal that asks. */
    private static final class Index271 {
        static final int INDEX = EntityClassCounts.trackedIndex(RED_PANDA_271);
    }

    /** Whether an AvoidEntityGoal's avoided class is a Critters and Companions RedPandaEntity (2.3.5 or 2.7.1, by exact name). */
    public static byte kind(Class<?> avoidClass) {
        int i = EntityClassCounts.index(avoidClass);
        if (i == EntityClassCounts.RED_PANDA) return RED_PANDA_GOAL;
        if (i >= 0 && i == Index271.INDEX) return RED_PANDA_271_GOAL;
        return OTHER_GOAL;
    }

    /** True when the 2.3.5 red-panda search of a goal in this level can only come back empty (the gate then answers). */
    public static boolean nothingToAvoid(Level level) {
        return nothingToAvoid(level, RED_PANDA_GOAL);
    }

    /** True when the red-panda search of a goal of this kind can only come back empty (the gate then answers). */
    public static boolean nothingToAvoid(Level level, byte kind) {
        int index = kind == RED_PANDA_GOAL ? EntityClassCounts.RED_PANDA : kind == RED_PANDA_271_GOAL ? Index271.INDEX : -1;
        if (index < 0 || !EntityClassCounts.none(level, index)) return false;
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
