package bons.furious.mixin.embeddium_hidden_blocks;

import bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.embeddium.api.render.chunk.BlockRenderContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderCache;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import org.embeddedt.embeddium.impl.world.WorldSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * embeddium_hidden_block_skip (Embeddium 1.0.15+mc1.21.1, LGPL-3.0; client).
 *
 * ChunkBuilderMeshingTask.execute calls BlockState.getRenderShape() twice per block: first in the air test,
 * then for "render shape == MODEL", the branch that asks the model for its data and layers and renders it. The value
 * of that second call is handed to HiddenBlocks.shape with the task's own render cache, render context, block state
 * and position (the first of its two mutable positions): for a block that provably emits nothing it becomes INVISIBLE,
 * so the branch is not entered; for every other block it is returned as it is. The task's world slice (the model data
 * of its blocks) is read only by the verify flag (HiddenBlocks.verify). No Embeddium code is carried.
 *
 * Ported to 1.21.1: execute is unchanged where this switch reads it (the air test, then "render shape == MODEL": model
 * data, seed, unwrap, one renderModel per render layer); the locals are the same types (one render cache, one render
 * context, one block state, the block position before the model offset, one world slice). The task has no model data
 * map there; the model data comes from the world slice, so the slice is handed on instead; Mojang names.
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public abstract class MeshingTaskHiddenBlockMixin {
    @ModifyExpressionValue(method = "execute", require = 1, allow = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape bons$hiddenBlock(RenderShape shape, @Local BlockRenderCache cache, @Local BlockRenderContext context, @Local BlockState state,
                                         @Local(ordinal = 0) BlockPos.MutableBlockPos pos, @Local WorldSlice slice) {
        return HiddenBlocks.shape(shape, cache, context, state, pos, slice);
    }
}
