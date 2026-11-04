package bons.furious.mixin.justdirethings_coo49;

import bons.furious.patch.justdirethings_coo49.AreaPreviews;
import com.direwolf20.justdirethings.client.events.RenderLevelLast;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * justdirethings_area_preview_chunk_memo (Just Dire Things [Forge] 1.1.11, client side; off by default; no Just Dire Things
 * code is carried). RenderLevelLast.renderAreaPreviews walks every nearby chunk's block entities every frame; the wrapped
 * getBlockEntities() answers an empty map for a chunk that held no shown area preview when it was last looked at, less than
 * a second ago. Trade-off: a preview switched on (or a new block with its preview on) can show up to a second later. See
 * AreaPreviews.
 */
@Mixin(value = RenderLevelLast.class, remap = false)
public abstract class AreaPreviewChunkMemoMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "renderAreaPreviews", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;m_62954_()Ljava/util/Map;"),
            require = 1, allow = 1)
    private static Map bons$justdirethingsAreaPreviewChunkMemo(LevelChunk chunk, Operation<Map> original) {
        return AreaPreviews.blockEntities(chunk, original);
    }
}
