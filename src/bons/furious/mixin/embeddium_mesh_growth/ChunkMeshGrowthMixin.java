package bons.furious.mixin.embeddium_mesh_growth;

import bons.furious.patch.embeddium_mesh_growth.MeshGrowth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * embeddium_mesh_buffer_growth_fix (Embeddium 0.3.31 for Minecraft 1.20.1 / Forge 47.4.16, client only). Fix.
 *
 * ChunkMeshBufferBuilder keeps its capacity in vertices: start() calls setBufferSize(initialCapacity = 131,072), and
 * setBufferSize(capacity) reallocates capacity * stride bytes. When one facing of one pass outgrows it (a section with more
 * than 131,072 vertices in one bucket: dense custom foliage, bushy-leaves packs), grow() computes the new vertex capacity
 * (twice the old) and then calls setBufferSize(newCapacity * stride): the stride is applied twice, so the builder
 * reallocates newCapacity * stride * stride bytes (262,144 x 20 x 20 = 105 MB for Embeddium's compact format, more for
 * Oculus's extended terrain format) and records a vertex capacity stride times too large, until the next start() shrinks
 * it back.
 *
 * This divides that one argument by the stride again (the stride itself, from the field it was multiplied with), so
 * setBufferSize receives the vertex capacity grow() computed: newCapacity * stride bytes. MemoryUtil.memRealloc keeps every
 * byte already written, and the writes go to the same offsets, so the mesh is the same; only the native allocation (and
 * the capacity at which the next growth happens) changes.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder", remap = false)
public abstract class ChunkMeshGrowthMixin {
    @Shadow(remap = false)
    @Final
    private int stride;

    @ModifyArg(method = "grow", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;setBufferSize(I)V"), remap = false)
    private int bons$vertexCapacity(int capacityTimesStride) {
        if (!MeshGrowth.enabled) return capacityTimesStride;
        MeshGrowth.grew(capacityTimesStride / this.stride, this.stride);
        return capacityTimesStride / this.stride;
    }
}
