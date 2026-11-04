package bons.furious.mixin.goal_presence_gates;

import bons.furious.patch.goal_presence_gates.GoalPresenceGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dummmmmmy_scarecrow_presence_gate (MmmMmmMmmMmm / dummmmmmy 1.20-2.0.12, Supplementaries Team License: no code carried;
 * server). ModEvents.onEntityJoinWorld gives animals that can be scared a vanilla AvoidEntityGoal for TargetDummyEntity (the
 * pumpkin-headed scarecrow) and, with the decoy option, every monster a NearestAttackableTargetGoal for TargetDummyEntity;
 * both search a box every goal evaluation even where no dummy exists. This wraps the two goal constructions and vouches for
 * the goals they return (GoalPresenceGates.vouch): vanilla_goal_class_presence_gate then skips their searches' section walks
 * while the mob's level holds no target dummy. The goals, their predicates (canScare / canAttract) and every other goal are
 * unchanged; COO throttles the scans instead (dummmmmmy.scarecrowScanInterval). See GoalPresenceGates.
 */
@Mixin(targets = "net.mehvahdjukaar.dummmmmmy.common.ModEvents", remap = false)
public abstract class DummmmmmyScarecrowVouchMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onEntityJoinWorld(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;FDDLjava/util/function/Predicate;)Lnet/minecraft/world/entity/ai/goal/AvoidEntityGoal;"),
            require = 1, allow = 1)
    private static AvoidEntityGoal bons$vouchScarecrow(PathfinderMob mob, Class<?> avoided, float distance, double walk, double sprint, Predicate<?> scares,
                                                       Operation<AvoidEntityGoal> original) {
        AvoidEntityGoal goal = original.call(mob, avoided, distance, walk, sprint, scares);
        return avoided != null && avoided.getName().equals(GoalPresenceGates.DUMMY) ? GoalPresenceGates.vouch(GoalPresenceGates.dummmmmmyScarecrow, goal, avoided) : goal;
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onEntityJoinWorld(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;IZZLjava/util/function/Predicate;)Lnet/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal;"),
            require = 1, allow = 1)
    private static NearestAttackableTargetGoal bons$vouchDecoy(Mob mob, Class<?> type, int interval, boolean mustSee, boolean mustReach, Predicate<?> attracts,
                                                               Operation<NearestAttackableTargetGoal> original) {
        NearestAttackableTargetGoal goal = original.call(mob, type, interval, mustSee, mustReach, attracts);
        return type != null && type.getName().equals(GoalPresenceGates.DUMMY) ? GoalPresenceGates.vouch(GoalPresenceGates.dummmmmmyScarecrow, goal, type) : goal;
    }
}
