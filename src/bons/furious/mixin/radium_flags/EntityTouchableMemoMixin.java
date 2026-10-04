package bons.furious.mixin.radium_flags;

import bons.furious.patch.radium_flags.EntityTouchableMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * radium_entity_touchable_memo (Radium Re-Reforged 0.14.3, both sides).
 *
 * Radium's ENTITY_TOUCHABLE block-state flag (the anonymous TrackedBlockStatePredicate BlockStateFlags$5) answers
 * test(state) = ReflectionUtil.hasMethodOverride(state.getBlock().getClass(), BlockBehaviour.class, true, "m_7892_",
 * BlockState, Level, BlockPos, Entity): a reflective walk up the block's class hierarchy, repeated for every block state.
 * The answer depends only on the block class, so it is kept per class after Radium's code answered for the first state
 * of that class (helper EntityTouchableMemo). Radium is LGPL-3.0; the mixin carries none of its code, only the per-class
 * memo around the original call.
 */
@Mixin(targets = "me.jellysquid.mods.lithium.common.block.BlockStateFlags$5", remap = false)
public abstract class EntityTouchableMemoMixin {
    @WrapMethod(method = "test(Lnet/minecraft/world/level/block/state/BlockState;)Z")
    private boolean bons$entityTouchableByClass(BlockState state, Operation<Boolean> original) {
        if (!EntityTouchableMemo.enabled) return original.call(state);
        Class<?> blockClass = state.m_60734_().getClass();
        Boolean known = EntityTouchableMemo.known(blockClass);
        if (known != null) {
            if (EntityTouchableMemo.SHADOW) EntityTouchableMemo.shadow(blockClass, known, original.call(state));
            return known;
        }
        boolean answer = original.call(state);
        EntityTouchableMemo.remember(blockClass, answer);
        return answer;
    }
}
