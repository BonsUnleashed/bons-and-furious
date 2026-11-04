package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.SortSkip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_order_independent_sort_skip, the Oculus part (Oculus 1.8.0 tested, client): Oculus's batched entity buffers
 * (net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder) sort the segment that ends (BufferBuilder
 * setQuadSorting) in getBuffer (m_6299_), getSegments and getSegmentsForType when its private static
 * shouldSortOnUpload(RenderType) says the type sorts on upload. MixinExtras @WrapOperation on those three
 * shouldSortOnUpload calls: the answer is false for an order-free type while no shader pack is in use (SortSkip.leaveOutSort,
 * the decision RenderType.end makes), so that segment ends unsorted; it is the stock answer otherwise. That covers Oculus's
 * batching without a shader pack (when oculus_vanilla_buffers_without_shaders is off); with a pack every segment keeps its
 * sort. @Pseudo: without Oculus the class does not exist and this mixin is skipped without a warning; require = 0: another
 * Oculus build without these calls leaves the class as it is. No @Shadow, so a changed Oculus class can never stop the game.
 * Oculus is LGPL-3.0-only: no Oculus code is carried; the wrapper only answers the sort question for order-free types.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder", remap = false)
public abstract class SegmentedSortSkipMixin {
    @WrapOperation(method = {"m_6299_", "getSegments", "getSegmentsForType"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/batchedentityrendering/impl/SegmentedBufferBuilder;shouldSortOnUpload(Lnet/minecraft/client/renderer/RenderType;)Z"))
    private boolean bons$segmentSorts(RenderType type, Operation<Boolean> original) {
        return original.call(type) && !SortSkip.leaveOutSort(type);
    }
}
