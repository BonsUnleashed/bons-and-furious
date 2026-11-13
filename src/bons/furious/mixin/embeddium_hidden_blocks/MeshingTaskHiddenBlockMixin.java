package bons.furious.mixin.embeddium_hidden_blocks;

import bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * embeddium_hidden_block_skip (Embeddium 0.3.31+mc1.20.1, LGPL-3.0; client).
 *
 * ChunkBuilderMeshingTask.execute calls BlockState.getRenderShape() (m_60799_) twice per block: first in the air test,
 * then for "render shape == MODEL", the branch that asks the model for its data and layers and renders it. The value
 * of that second call is handed to HiddenBlocks.shape with the task's own render cache, render context, block state
 * and position (the first of its two mutable positions): for a block that provably emits nothing it becomes INVISIBLE,
 * so the branch is not entered; for every other block it is returned as it is. The task's model data map is read only
 * by the verify flag (HiddenBlocks.verify). No Embeddium code is carried.
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public abstract class MeshingTaskHiddenBlockMixin {
    @Shadow @Final private Map<BlockPos, ModelData> modelDataMap;

    @ModifyExpressionValue(method = "execute", require = 1, allow = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/level/block/state/BlockState;m_60799_()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape bons$hiddenBlock(RenderShape shape, @Local BlockRenderCache cache, @Local BlockRenderContext context, @Local BlockState state,
                                         @Local(ordinal = 0) BlockPos.MutableBlockPos pos) {
        return HiddenBlocks.shape(shape, cache, context, state, pos, this.modelDataMap);
    }
}
