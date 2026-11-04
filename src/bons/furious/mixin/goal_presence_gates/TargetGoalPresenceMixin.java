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
 * vanilla_goal_class_presence_gate (Minecraft 1.20.1 NearestAttackableTargetGoal, SRG class, not patched by Forge 47.4.28;
 * server in practice). Wraps findTarget (m_26073_, called by canUse after its randomInterval draw): a goal a caller key
 * vouched for (bons$presence != 0) whose level holds no instance of its target class runs findTarget with its search marked
 * EMPTY, so vanilla_entity_walk_skip leaves out the walk that could find nothing; findTarget itself runs unchanged (target =
 * null, the same profiler count, Apothic Attributes' injection at its head as before). Every other goal runs the original
 * directly. One int per goal. See GoalPresenceGates. No Minecraft code is carried.
 */
@Mixin(value = NearestAttackableTargetGoal.class, remap = false)
public abstract class TargetGoalPresenceMixin extends TargetGoal implements GoalPresenceGates.Vouched {
    @Shadow
    @Final
    protected Class<?> f_26048_;
    @Shadow
    protected LivingEntity f_26050_;
    @Unique
    private int bons$presence;

    private TargetGoalPresenceMixin(Mob mob) {
        super(mob, false, false);
    }

    @WrapMethod(method = "m_26073_")
    private void bons$presenceGate(Operation<Void> original) {
        int vouch = this.bons$presence;
        if (vouch == 0) {
            original.call();
            return;
        }
        GoalPresenceGates.findTarget(vouch, this.f_26135_.m_9236_(), this.f_26048_, () -> this.f_26050_, original);
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
