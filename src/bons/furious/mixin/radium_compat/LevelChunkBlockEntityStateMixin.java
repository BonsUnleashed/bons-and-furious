package bons.furious.mixin.radium_compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_block_entity_tick_state, part 3 of 3: LevelChunk.setBlockState keeps the block entity's state equal to the block
 * in the world.
 *
 * setBlockState calls the new block's onPlace before it creates or updates the block entity, and onPlace may set a different
 * state of the same block (a hopper placed on a powered block, for example). Vanilla then gives the block entity the state
 * it was asked to place, not the one now in the world; vanilla's tick does not notice, because it looks the state up again
 * every tick. These two wraps do what Radium's support_cache WorldChunkMixin does (its CAPTURE_FAILHARD local capture
 * cannot apply on Forge, whose setBlockState has two more locals):
 *  - a new block entity is created with the state now in the world when that is a state of the same block (when onPlace put
 *    another block there, the vanilla state is kept: that block entity is never added to the chunk);
 *  - after an existing block entity received the requested state, it receives the world's state as well when they differ.
 */
@Mixin(value = LevelChunk.class, remap = false)
public abstract class LevelChunkBlockEntityStateMixin {
    @Shadow
    public abstract BlockState m_8055_(BlockPos pos);

    @WrapOperation(method = "m_6978_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/EntityBlock;m_142194_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity bons$createWithWorldState(EntityBlock block, BlockPos pos, BlockState state, Operation<BlockEntity> original) {
        BlockState now = this.m_8055_(pos);
        return original.call(block, pos, now != state && now.m_60734_() == state.m_60734_() ? now : state);
    }

    @WrapOperation(method = "m_6978_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;m_155250_(Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void bons$keepWorldState(BlockEntity blockEntity, BlockState state, Operation<Void> original, @Local(argsOnly = true) BlockPos pos) {
        original.call(blockEntity, state);
        BlockState now = this.m_8055_(pos);
        if (now != state) {
            original.call(blockEntity, now);
        }
    }
}
