package bons.furious.mixin.embeddium;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * embeddium_lazy_completed_jobs (Embeddium 0.3.31+mc1.20.1).
 *
 * uploadChunks runs every frame and began with collectChunkBuildResults(), which allocated an ArrayList even when no
 * chunk build had finished, only for uploadChunks to return on isEmpty(). ac$collectAvailable polls the first result
 * before allocating and returns null when there is none; otherwise it drains the queue in the same order into the same
 * kind of list, and uploadChunks processes it exactly as before.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerMixin {
    @Shadow @Final private ConcurrentLinkedDeque<ChunkJobResult<ChunkBuildOutput>> buildResults;
    @Shadow private boolean needsUpdate;

    @Shadow
    private void processChunkBuildResults(ArrayList<ChunkBuildOutput> results) {
        // shadowed: the body is the installed method's
    }

    /**
     * @author BonsUnleashed
     * @reason Skip the per-frame ArrayList when no chunk build has finished.
     */
    @Overwrite
    public void uploadChunks() {
        ArrayList<ChunkBuildOutput> results = this.ac$collectAvailable();
        if (results == null) {
            return;
        }
        this.processChunkBuildResults(results);
        for (ChunkBuildOutput result : results) {
            result.delete();
        }
        this.needsUpdate = true;
    }

    /** The finished build outputs in queue order, or null when the queue is empty (no list allocated). */
    @Unique
    private ArrayList<ChunkBuildOutput> ac$collectAvailable() {
        ChunkJobResult<ChunkBuildOutput> first = this.buildResults.poll();
        if (first == null) {
            return null;
        }
        ArrayList<ChunkBuildOutput> results = new ArrayList<>();
        results.add(first.unwrap());
        ChunkJobResult<ChunkBuildOutput> next;
        while ((next = this.buildResults.poll()) != null) {
            results.add(next.unwrap());
        }
        return results;
    }
}
