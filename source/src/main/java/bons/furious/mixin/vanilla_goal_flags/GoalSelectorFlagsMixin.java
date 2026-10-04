package bons.furious.mixin.vanilla_goal_flags;

import bons.furious.patch.vanilla_goal_flags.GoalFlags;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_goal_flags_none_disabled (Minecraft 1.21.1 with NeoForge 21.1.252): GoalSelector.goalContainsAnyFlags loops over
 * the goal's flags testing the selector's disabled set; while that set is empty the loop gets an empty flag set (the
 * goal's getFlags is still called once). Helper: {@link GoalFlags}.
 *
 * Ported to 1.21.1: Mojang names only; goalContainsAnyFlags, disabledFlags and its two callers in tick are unchanged
 * (Radium 0.13.1's collections.goals mixin only replaces availableGoals).
 */
@Mixin(value = GoalSelector.class, remap = false)
public abstract class GoalSelectorFlagsMixin {
    @Redirect(method = "goalContainsAnyFlags", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/WrappedGoal;getFlags()Ljava/util/EnumSet;"),
            require = 1, allow = 1)
    private static EnumSet<Goal.Flag> bons$flagsToTest(WrappedGoal goal, WrappedGoal testedGoal, EnumSet<Goal.Flag> disabled) {
        return GoalFlags.flags(goal.getFlags(), disabled);
    }
}
