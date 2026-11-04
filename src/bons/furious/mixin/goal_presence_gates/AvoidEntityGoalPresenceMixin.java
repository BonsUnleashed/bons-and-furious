package bons.furious.mixin.goal_presence_gates;

import bons.furious.patch.goal_presence_gates.GoalPresenceGates;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_goal_class_presence_gate (Minecraft 1.20.1 AvoidEntityGoal, SRG class, not patched by Forge 47.4.28; server in
 * practice). Wraps canUse (m_8036_): a goal a caller key vouched for (bons$presence != 0, set where the goal is made) whose
 * level holds no instance of its avoided class runs canUse with its search marked EMPTY, so vanilla_entity_walk_skip leaves
 * out the walk that could find nothing; canUse itself runs unchanged (same toAvoid = null, same false, same profiler count,
 * Valkyrien Skies' and Radium's hooks inside it as before). Every other goal runs the original directly. One int per goal.
 * A WrapMethod (around the whole method) because Radium's nearby-entity-tracking option redirects the search call inside it.
 * See GoalPresenceGates. No Minecraft code is carried.
 */
@Mixin(value = AvoidEntityGoal.class, remap = false)
public abstract class AvoidEntityGoalPresenceMixin implements GoalPresenceGates.Vouched {
    @Shadow
    @Final
    protected PathfinderMob f_25015_;
    @Shadow
    protected LivingEntity f_25016_;
    @Shadow
    @Final
    protected Class<?> f_25020_;
    @Unique
    private int bons$presence;

    @WrapMethod(method = "m_8036_")
    private boolean bons$presenceGate(Operation<Boolean> original) {
        int vouch = this.bons$presence;
        if (vouch == 0) return original.call();
        return GoalPresenceGates.avoidCanUse(vouch, this.f_25015_.m_9236_(), this.f_25020_, () -> this.f_25016_, original);
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
