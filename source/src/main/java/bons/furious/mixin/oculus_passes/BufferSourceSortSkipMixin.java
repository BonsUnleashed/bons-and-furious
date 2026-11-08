package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.SortSkip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_order_independent_sort_skip (Minecraft 1.21.1 on NeoForge 21.1.252, client; `change`, off by default).
 * MultiBufferSource.BufferSource.endBatch(RenderType, BufferBuilder) builds the batch's MeshData and, for a type created
 * with sortOnUpload, calls MeshData.sortQuads(fixedBuffers.getOrDefault(type, sharedBuffer), RenderSystem
 * .getVertexSorting()) before RenderType.draw; the SortState it returns is discarded. MixinExtras @WrapOperation on that
 * call: it is left out (null, the value nobody reads) when the batch's render type is order-free
 * (OrderFreeType.bons$orderFreeQuads) and no shader pack is in use (SortSkip.leaveOutSort); otherwise the call itself.
 * The MeshData then keeps no index buffer, VertexBuffer.upload binds the shared sequential index buffer and the quads are
 * drawn in buffer order; the vertex bytes are the same. ImmediatelyFast's batching buffer source ends its batches through
 * this same method (drawDirect), so it is covered too; Iris's injectors around RenderType.draw in this method are
 * untouched. require = 0: a mod that rewrites endBatch keeps its sort. Carries no Minecraft code.
 */
@Mixin(value = MultiBufferSource.BufferSource.class, remap = false)
public abstract class BufferSourceSortSkipMixin {
    @WrapOperation(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/MeshData;sortQuads(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)Lcom/mojang/blaze3d/vertex/MeshData$SortState;"))
    private MeshData.SortState bons$sortQuads(MeshData mesh, ByteBufferBuilder buffer, VertexSorting sorting, Operation<MeshData.SortState> original,
                                             @Local(argsOnly = true) RenderType type) {
        return SortSkip.leaveOutSort(type) ? null : original.call(mesh, buffer, sorting);
    }
}
