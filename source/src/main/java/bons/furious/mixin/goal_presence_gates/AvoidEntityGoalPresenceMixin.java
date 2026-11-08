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
 * vanilla_goal_class_presence_gate (Minecraft 1.21.1 AvoidEntityGoal, not patched by NeoForge 21.1.252; server in
 * practice). Wraps canUse()Z: a goal a caller key vouched for (bons$presence != 0, set where the goal is made) whose level
 * holds no instance of its avoided class runs canUse with its search (getEntitiesOfClass(avoidClass, ...)) marked EMPTY,
 * so vanilla_entity_walk_skip leaves out the walk that could find nothing; canUse itself runs unchanged (same toAvoid =
 * null, same false, same profiler count, every other hook inside it as before). Every other goal runs the original
 * directly. One int per goal. A WrapMethod (around the whole method), so another mod's redirect of the search call inside
 * it stays as it is; the wrap of crittersandcompanions_red_panda_gate on the same method chains with this one. See
 * GoalPresenceGates. No Minecraft code is carried.
 */
@Mixin(value = AvoidEntityGoal.class, remap = false)
public abstract class AvoidEntityGoalPresenceMixin implements GoalPresenceGates.Vouched {
    @Shadow
    @Final
    protected PathfinderMob mob;
    @Shadow
    protected LivingEntity toAvoid;
    @Shadow
    @Final
    protected Class<?> avoidClass;
    @Unique
    private int bons$presence;

    @WrapMethod(method = "canUse()Z")
    private boolean bons$presenceGate(Operation<Boolean> original) {
        int vouch = this.bons$presence;
        if (vouch == 0) return original.call();
        return GoalPresenceGates.avoidCanUse(vouch, this.mob.level(), this.avoidClass, () -> this.toAvoid, original);
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
