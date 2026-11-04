package bons.furious.mixin.goal_presence_gates;

import bons.furious.patch.goal_presence_gates.GoalPresenceGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * primitivemobs_avoid_presence_gate, sheepman half (Primitive Mobs Revived 1.0.4, CC BY 3.0: no code carried; server).
 * Sheepman.registerGoals gives every sheepman a vanilla AvoidEntityGoal for ZombifiedPiglin (12 blocks); it searches a
 * 25 x 7 x 25 box every other tick. This wraps that goal construction and vouches for the goal (GoalPresenceGates.vouch):
 * vanilla_goal_class_presence_gate then skips its search's section walk while the level holds no zombified piglin (the
 * vanilla class is counted only while Primitive Mobs is loaded: its registration names the mod). COO throttles the goal
 * instead (primitivemobs.sheepmanPiglinAvoidInterval). See GoalPresenceGates.
 */
@Mixin(targets = "com.misanthropy.primitive_mobs.entity.passive.Sheepman", remap = false)
public abstract class PrimitiveMobsSheepmanVouchMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "m_8099_()V",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;FDD)Lnet/minecraft/world/entity/ai/goal/AvoidEntityGoal;"),
            require = 1, allow = 1)
    private AvoidEntityGoal bons$vouch(PathfinderMob mob, Class<?> avoided, float distance, double walk, double sprint, Operation<AvoidEntityGoal> original) {
        AvoidEntityGoal goal = original.call(mob, avoided, distance, walk, sprint);
        return avoided != null && avoided.getName().equals(GoalPresenceGates.ZOMBIFIED_PIGLIN) ? GoalPresenceGates.vouch(GoalPresenceGates.primitivemobsAvoid, goal, avoided) : goal;
    }
}
