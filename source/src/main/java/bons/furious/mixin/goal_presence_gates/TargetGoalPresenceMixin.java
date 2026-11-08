package bons.furious.mixin.goal_presence_gates;

import bons.furious.patch.goal_presence_gates.GoalPresenceGates;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_goal_class_presence_gate (Minecraft 1.21.1 NearestAttackableTargetGoal, not patched by NeoForge 21.1.252; server
 * in practice). Wraps findTarget()V (called by canUse after its randomInterval draw): a goal a caller key vouched for
 * (bons$presence != 0) whose level holds no instance of its target class runs findTarget with its search
 * (getEntitiesOfClass(targetType, getTargetSearchArea(...)), for every target type but Player and ServerPlayer) marked
 * EMPTY, so vanilla_entity_walk_skip leaves out the walk that could find nothing; findTarget itself runs unchanged (target
 * = null, the same profiler count, every other hook inside it as before). Every other goal runs the original directly.
 * One int per goal. See GoalPresenceGates. No Minecraft code is carried.
 */
@Mixin(value = NearestAttackableTargetGoal.class, remap = false)
public abstract class TargetGoalPresenceMixin extends TargetGoal implements GoalPresenceGates.Vouched {
    @Shadow
    @Final
    protected Class<?> targetType;
    @Shadow
    protected LivingEntity target;
    @Unique
    private int bons$presence;

    private TargetGoalPresenceMixin(Mob mob) {
        super(mob, false, false);
    }

    @WrapMethod(method = "findTarget()V")
    private void bons$presenceGate(Operation<Void> original) {
        int vouch = this.bons$presence;
        if (vouch == 0) {
            original.call();
            return;
        }
        GoalPresenceGates.findTarget(vouch, this.mob.level(), this.targetType, () -> this.target, original);
    }

    @Override
    public int bons$presence() {
        return this.bons$presence;
    }

    @Override
    public void bons$presence(int vouch) {
        this.bons$presence = vouch;
    }
}
