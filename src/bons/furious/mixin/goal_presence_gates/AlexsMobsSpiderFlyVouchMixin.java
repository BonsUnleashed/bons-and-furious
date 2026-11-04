package bons.furious.mixin.goal_presence_gates;

import bons.furious.patch.goal_presence_gates.GoalPresenceGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * alexsmobs_spider_fly_presence_gate (Alex's Mobs 1.22.9, LGPL-3.0: no code carried; server). ServerEvents.
 * onEntityFinalizeSpawn gives every spawning spider (config spidersAttackFlies) a vanilla NearestAttackableTargetGoal for
 * EntityFly with randomInterval 1, so its findTarget searches a 33 x 9 x 33 box at every goal tick. This wraps the
 * NearestAttackableTargetGoal constructions of the method (four: flies, seals, jerboas, flying fish) and vouches only for the
 * fly goal (GoalPresenceGates.vouch): vanilla_goal_class_presence_gate then skips its search's section walk while the
 * spider's level holds no fly. canUse's randomInterval draw and every other goal are unchanged; COO throttles the scan instead
 * (alexsmobs.spiderFlyScanInterval). See GoalPresenceGates.
 */
@Mixin(targets = "com.github.alexthe666.alexsmobs.event.ServerEvents", remap = false)
public abstract class AlexsMobsSpiderFlyVouchMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onEntityFinalizeSpawn(Lnet/minecraftforge/event/entity/living/MobSpawnEvent$FinalizeSpawn;)V",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;IZZLjava/util/function/Predicate;)Lnet/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal;"),
            require = 4, allow = 4)
    private NearestAttackableTargetGoal bons$vouch(Mob mob, Class<?> type, int interval, boolean mustSee, boolean mustReach, Predicate<?> selector,
                                                   Operation<NearestAttackableTargetGoal> original) {
        NearestAttackableTargetGoal goal = original.call(mob, type, interval, mustSee, mustReach, selector);
        return type != null && type.getName().equals(GoalPresenceGates.FLY) ? GoalPresenceGates.vouch(GoalPresenceGates.alexsmobsSpiderFly, goal, type) : goal;
    }
}
