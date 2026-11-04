package bons.furious.mixin.goal_presence_gates;

import bons.furious.patch.goal_presence_gates.GoalPresenceGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * primitivemobs_avoid_presence_gate, villager half (Primitive Mobs Revived 1.0.4, CC BY 3.0: no code carried; server).
 * PrimitiveMobsEvents.onEntityJoin gives every villager that joins a server level a vanilla AvoidEntityGoal for the mod's
 * Goblin (8 blocks); it searches a 17 x 7 x 17 box every other tick. This wraps that goal construction and vouches for the goal
 * (GoalPresenceGates.vouch): vanilla_goal_class_presence_gate then skips its search's section walk while the level holds no
 * goblin. COO throttles the goal instead (primitivemobs.villagerGoblinAvoidInterval). See GoalPresenceGates.
 */
@Mixin(targets = "com.misanthropy.primitive_mobs.core.PrimitiveMobsEvents", remap = false)
public abstract class PrimitiveMobsGoblinVouchMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onEntityJoin(Lnet/minecraftforge/event/entity/EntityJoinLevelEvent;)V",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;FDD)Lnet/minecraft/world/entity/ai/goal/AvoidEntityGoal;"),
            require = 1, allow = 1)
    private static AvoidEntityGoal bons$vouch(PathfinderMob mob, Class<?> avoided, float distance, double walk, double sprint, Operation<AvoidEntityGoal> original) {
        AvoidEntityGoal goal = original.call(mob, avoided, distance, walk, sprint);
        return avoided != null && avoided.getName().equals(GoalPresenceGates.GOBLIN) ? GoalPresenceGates.vouch(GoalPresenceGates.primitivemobsAvoid, goal, avoided) : goal;
    }
}
