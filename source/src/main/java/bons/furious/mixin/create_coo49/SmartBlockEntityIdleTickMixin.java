package bons.furious.mixin.create_coo49;

import bons.furious.patch.create_coo49.IdleBehaviours;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * create_idle_behaviour_tick_skip (Create 6.0.10 for Minecraft 1.21.1, both sides): the forEachBehaviour(
 * BlockEntityBehaviour::tick) call at the end of SmartBlockEntity.tick walks getAllBehaviours().forEach with
 * IdleBehaviours.GATED_TICK instead (same collection, same order, tick() only for behaviour classes that override tick or
 * lazyTick), unless the switch is off or this block entity's class has its own forEachBehaviour / getAllBehaviours (cached
 * in a byte here). Exact variant; see IdleBehaviours. A WrapOperation composes with another mod's injections into tick.
 */
@Mixin(value = SmartBlockEntity.class, remap = false)
public abstract class SmartBlockEntityIdleTickMixin {
    @Unique
    private byte bons$walkClass;

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;forEachBehaviour(Ljava/util/function/Consumer;)V"),
            require = 1, allow = 1)
    private void bons$skipIdleBehaviourTicks(SmartBlockEntity self, Consumer<BlockEntityBehaviour> action, Operation<Void> original) {
        if (IdleBehaviours.enabled) {
            byte w = this.bons$walkClass;
            if (w == 0) this.bons$walkClass = w = IdleBehaviours.classifyEntity(self.getClass());
            if (w == 1) {
                self.getAllBehaviours().forEach(IdleBehaviours.GATED_TICK);
                return;
            }
        }
        original.call(self, action);
    }
}
