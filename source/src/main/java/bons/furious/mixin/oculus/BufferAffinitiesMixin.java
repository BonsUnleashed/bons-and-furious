package bons.furious.mixin.oculus;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.irisshaders.batchedentityrendering.impl.BufferSegment;
import net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource;
import net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder;
import net.irisshaders.batchedentityrendering.impl.ordering.RenderOrderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * oculus_primitive_buffer_affinities (Oculus 1.8.0 for Minecraft 1.20.1).
 *
 * getBuffer (getBuffer) runs for every buffer request of batched entity rendering and kept its render type to buffer
 * LRU cache in an access-ordered LinkedHashMap of boxed Integers, evicting through an entry-set iterator. The cache now
 * lives in a primitive Object2IntLinkedOpenHashMap (ac$affinities) holding buffer index + 1, so 0 means "not cached":
 * getAndMoveToLast refreshes an entry, removeFirstInt evicts the least recently used one, and readyUp clears it as
 * before. A mixin cannot change the declared type of the original field, so the new map has its own field; the old
 * field keeps the empty LinkedHashMap the constructor creates and is no longer read.
 */
@Mixin(value = FullyBufferedMultiBufferSource.class, remap = false)
public abstract class BufferAffinitiesMixin {
    @Shadow @Final private RenderOrderManager renderOrderManager;
    @Shadow @Final private SegmentedBufferBuilder[] builders;
    @Shadow @Final private Map<RenderType, List<BufferSegment>> typeToSegment;
    @Shadow private Function<RenderType, RenderType> wrappingFunction;
    @Shadow private boolean isReady;
    @Shadow private List<RenderType> renderOrder;

    /** Render type to buffer index + 1, least recently used first (the default return value 0 means absent). */
    @Unique
    private final Object2IntLinkedOpenHashMap<RenderType> ac$affinities = new Object2IntLinkedOpenHashMap<>(32, 0.75F);

    @Shadow
    private void removeReady() {
        throw new AssertionError();
    }

    /**
     * @author BonsUnleashed
     * @reason Keep the render type to buffer LRU cache in a primitive map instead of boxing and iterator eviction.
     */
    @Overwrite
    public VertexConsumer getBuffer(RenderType renderType) {
        this.removeReady();
        if (this.wrappingFunction != null) {
            renderType = this.wrappingFunction.apply(renderType);
        }
        this.renderOrderManager.begin(renderType);
        int affinity = this.ac$affinities.getAndMoveToLast(renderType);
        if (affinity == 0) {
            affinity = this.ac$affinities.size() < this.builders.length ? this.ac$affinities.size() + 1 : this.ac$affinities.removeFirstInt();
            this.ac$affinities.putAndMoveToLast(renderType, affinity);
        }
        return this.builders[affinity - 1].getBuffer(renderType);
    }

    /**
     * @author BonsUnleashed
     * @reason Clear the primitive affinity cache (the only change; the rest is the original method).
     */
    @Overwrite
    public void readyUp() {
        this.isReady = true;
        ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
        profiler.push("collect");
        for (SegmentedBufferBuilder builder : this.builders) {
            List<BufferSegment> segments = builder.getSegments();
            for (BufferSegment segment : segments) {
                this.typeToSegment.computeIfAbsent(segment.type(), type -> new ArrayList<>()).add(segment);
            }
        }
        profiler.popPush("resolve ordering");
        this.renderOrder = this.renderOrderManager.getRenderOrder();
        this.renderOrderManager.reset();
        this.ac$affinities.clear();
        profiler.pop();
    }
}
