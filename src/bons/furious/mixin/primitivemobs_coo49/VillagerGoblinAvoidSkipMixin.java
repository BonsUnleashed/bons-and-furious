package bons.furious.mixin.primitivemobs_coo49;

import bons.furious.patch.primitivemobs_coo49.PrimitiveScans;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * primitivemobs_skip_villager_goblin_avoid (Primitive Mobs 1.0.4, server; trade-off, off by default): a
 * MixinExtras @WrapWithCondition on the one GoalSelector.addGoal (m_25352_) call of PrimitiveMobsEvents.onEntityJoin,
 * the call that gives a villager joining a server level the AvoidEntityGoal against goblins: left out while the switch
 * is on (PrimitiveScans). The goal object is still built (another hook may look at it); it is just not added.
 */
@Mixin(targets = "com.misanthropy.primitive_mobs.core.PrimitiveMobsEvents", remap = false)
public abstract class VillagerGoblinAvoidSkipMixin {
    @WrapWithCondition(method = "onEntityJoin(Lnet/minecraftforge/event/entity/EntityJoinLevelEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;m_25352_(ILnet/minecraft/world/entity/ai/goal/Goal;)V"),
            require = 1, allow = 1)
    private static boolean bons$primitivemobsCoo49AddGoblinAvoid(GoalSelector selector, int priority, Goal goal) {
        return PrimitiveScans.addVillagerGoblinAvoid();
    }
}
