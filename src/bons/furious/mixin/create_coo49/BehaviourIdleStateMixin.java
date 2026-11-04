package bons.furious.mixin.create_coo49;

import bons.furious.patch.create_coo49.IdleBehaviourState;
import bons.furious.patch.create_coo49.IdleBehaviours;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * create_idle_behaviour_tick_skip (Create 6.0.8): one byte on BlockEntityBehaviour caching whether its runtime class is
 * idle on tick (IdleBehaviours.classify; 0 = not decided yet, races only repeat the same answer). The behaviour itself is
 * unchanged; only SmartBlockEntityIdleTickMixin's walk reads it.
 */
@Mixin(value = BlockEntityBehaviour.class, remap = false)
public abstract class BehaviourIdleStateMixin implements IdleBehaviourState {
    @Unique
    private byte bons$idleClass;

    @Override
    public boolean bons$idleBehaviour() {
        byte s = this.bons$idleClass;
        if (s == 0) this.bons$idleClass = s = IdleBehaviours.classify(this.getClass());
        return s == 1;
    }
}
