package bons.furious.patch.vanilla_goal_flags;

import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.ai.goal.Goal;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_goal_flags_none_disabled (Minecraft 1.21.1 with NeoForge 21.1.252; both sides, runs where
 * goal selectors tick: the server thread).
 *
 * GoalSelector.tick asks goalContainsAnyFlags(goal, disabledFlags) once per goal per selector tick: a loop over
 * the goal's flag set (WrappedGoal.getFlags) testing disabledFlags.contains for each flag. A selector's disabled set is
 * empty unless the mob is steered by a rider or sits in a boat, and then every contains answers false: the loop decides
 * nothing. GoalSelectorFlagsMixin redirects the loop's getFlags call: the call is made as before (once, same receiver),
 * and while the disabled set is empty the loop gets an empty flag set, so it returns false without iterating. A null flag
 * set, or any non-empty disabled set, goes to the loop unchanged.
 *
 * -Dbons_and_furious.goalFlagsNoneDisabled=false hands every loop the goal's own flag set.
 * -Dbons_and_furious.goalFlagsNoneDisabled.shadow=true (verification) also runs the original loop over the goal's flags
 * each time and counts any flag it would have found (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged).
 *
 * Ported to 1.21.1: unchanged logic; goalContainsAnyFlags and the disabled-flag bookkeeping (disable/enable/setControlFlag)
 * are identical on 1.21.1 (only tick's lockedFlags cleanup became a removeIf, which this switch does not touch).
 */
public final class GoalFlags {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.goalFlagsNoneDisabled", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.goalFlagsNoneDisabled.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Only ever iterated by goalContainsAnyFlags' loop; nothing writes it (checked on every use anyway). */
    private static final EnumSet<Goal.Flag> NONE = EnumSet.noneOf(Goal.Flag.class);
    private static boolean announced;

    private GoalFlags() {
    }

    /** The flag set goalContainsAnyFlags' loop iterates: an empty one while nothing is disabled, else the goal's own. */
    public static EnumSet<Goal.Flag> flags(EnumSet<Goal.Flag> flags, EnumSet<Goal.Flag> disabled) {
        if (disabled == null || !disabled.isEmpty() || flags == null || !enabled) return flags;
        EnumSet<Goal.Flag> none = NONE;
        if (!none.isEmpty()) return flags;
        if (!announced) announce();
        if (SHADOW) shadow(flags, disabled);
        return none;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: vanilla_goal_flags_none_disabled applies (goal selectors with no disabled control flag skip the per-goal flag loop){}",
                SHADOW ? " - shadow verification on" : "");
    }

    private static void shadow(EnumSet<Goal.Flag> flags, EnumSet<Goal.Flag> disabled) {
        SHADOW_CHECKS.incrementAndGet();
        for (Goal.Flag f : flags) {
            if (disabled.contains(f)) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_goal_flags_none_disabled shadow mismatch #{}: flag {} of {} is disabled in {}", m, f, flags, disabled);
                return;
            }
        }
    }
}
