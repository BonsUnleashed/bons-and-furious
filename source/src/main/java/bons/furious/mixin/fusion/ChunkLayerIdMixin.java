package bons.furious.mixin.fusion;

import bons.furious.patch.fusion.ChunkLayerIds;
import com.supermartijn642.fusion.util.ChunkRenderTypeHelper;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * fusion_chunk_layer_id (Fusion 1.3.14+a, client).
 *
 * The two lookups inside ChunkRenderTypeHelper (the layer list's contains in isChunkRenderType, the id map's get in
 * getId) are answered from Forge's RenderType.getChunkLayerId(), which numbers the same five layers in the same order;
 * see ChunkLayerIds for why every answer equals the original one. The rest of both methods (getId's null check and its
 * IllegalArgumentException for a non-chunk type) is untouched. Fusion is All Rights Reserved: this mixin redirects two
 * calls and carries none of its code. With the runtime flag off the original calls run.
 */
@Mixin(value = ChunkRenderTypeHelper.class, remap = false)
public abstract class ChunkLayerIdMixin {
    @Redirect(method = "isChunkRenderType", at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private static boolean bons$chunkLayer(List<?> layers, Object type) {
        return ChunkLayerIds.enabled ? ChunkLayerIds.isChunkLayer(type) : layers.contains(type);
    }

    @Redirect(method = "getId", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$layerId(Map<?, ?> ids, Object type) {
        return ChunkLayerIds.enabled ? ChunkLayerIds.id(type) : ids.get(type);
    }
}
