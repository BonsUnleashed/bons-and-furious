package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.SortSkip;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * vanilla_order_independent_sort_skip, the Iris part (Iris 1.8.12 for Minecraft 1.21.1, client): Iris's batched entity
 * buffers (net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder) build one segment per render type in
 * getSegments (its lambda lambda$getSegments$2) and sort it (MeshData.sortQuads) when the private static
 * shouldSortOnUpload(RenderType) says the type sorts on upload; FullyBufferedMultiBufferSource draws the segments with
 * RenderType.draw. MixinExtras @WrapMethod around shouldSortOnUpload: the answer is false for an order-free type while no
 * shader pack is in use (SortSkip.leaveOutSort, the decision BufferSource.endBatch makes), so that segment stays
 * unsorted; it is the stock answer otherwise. Iris routes every entity draw of a level frame through these buffers, also
 * without a shader pack; with a pack every segment keeps its sort. @Pseudo: without Iris the class does not exist and
 * this mixin is skipped without a warning; require = 0: another Iris build without the method leaves the class as it is.
 * No @Shadow, so a changed Iris class can never stop the game. Iris is LGPL-3.0-only: no Iris code is carried; the
 * wrapper only answers the sort question for order-free types.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder", remap = false)
public abstract class SegmentedSortSkipMixin {
    @WrapMethod(method = "shouldSortOnUpload(Lnet/minecraft/client/renderer/RenderType;)Z", require = 0)
    private static boolean bons$segmentSorts(RenderType type, Operation<Boolean> original) {
        return original.call(type) && !SortSkip.leaveOutSort(type);
    }
}
