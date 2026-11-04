package bons.furious.mixin.goal_presence_gates;

import bons.furious.patch.goal_presence_gates.GoalPresenceGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * alexsmobs_creeper_avoid_presence_gate (Alex's Mobs 1.22.9, LGPL-3.0: no code carried; server). ServerEvents.
 * onEntityFinalizeSpawn gives every spawning creeper two vanilla AvoidEntityGoals, for EntitySnowLeopard and EntityTiger; each
 * searches a 13 x 7 x 13 box every other tick. This wraps those two goal constructions and vouches for the goals they return
 * (GoalPresenceGates.vouch): vanilla_goal_class_presence_gate then skips their searches' section walks while the creeper's
 * level holds no snow leopard / tiger. The goals themselves, their priority and every other goal are unchanged; COO removes
 * them instead (alexsmobs.skipCreeperAvoidGoals). See GoalPresenceGates.
 */
@Mixin(targets = "com.github.alexthe666.alexsmobs.event.ServerEvents", remap = false)
public abstract class AlexsMobsCreeperAvoidVouchMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onEntityFinalizeSpawn(Lnet/minecraftforge/event/entity/living/MobSpawnEvent$FinalizeSpawn;)V",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;FDD)Lnet/minecraft/world/entity/ai/goal/AvoidEntityGoal;"),
            require = 2, allow = 2)
    private AvoidEntityGoal bons$vouch(PathfinderMob mob, Class<?> avoided, float distance, double walk, double sprint, Operation<AvoidEntityGoal> original) {
        AvoidEntityGoal goal = original.call(mob, avoided, distance, walk, sprint);
        String n = avoided == null ? "" : avoided.getName();
        return n.equals(GoalPresenceGates.SNOW_LEOPARD) || n.equals(GoalPresenceGates.TIGER) ? GoalPresenceGates.vouch(GoalPresenceGates.alexsmobsCreeperAvoid, goal, avoided) : goal;
    }
}
