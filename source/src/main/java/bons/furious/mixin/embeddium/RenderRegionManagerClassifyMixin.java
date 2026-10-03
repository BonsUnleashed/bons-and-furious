package bons.furious.mixin.embeddium;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * embeddium_upload_classification (Embeddium 0.3.31+mc1.20.1).
 *
 * uploadMeshes split each region's finished chunk builds into mesh uploads and index-only (resort) uploads with two
 * stream().filter(...).toList() pipelines per region, every frame that has results. ac$classify does the same split
 * with an indexed loop and allocates a list only when an output matches (an empty group gets Collections.emptyList()).
 * Both results are unmodifiable lists with the same elements in the same order; a list that is not a plain ArrayList
 * of plain ChunkBuildOutput objects still goes through the original stream code.
 */
@Mixin(value = RenderRegionManager.class, remap = false)
public abstract class RenderRegionManagerClassifyMixin {
    @Shadow
    private Reference2ReferenceMap.FastEntrySet<RenderRegion, List<ChunkBuildOutput>> createMeshUploadQueues(Collection<ChunkBuildOutput> results) {
        throw new AssertionError("shadowed");
    }

    @Shadow
    private void uploadMeshes(CommandList commandList, RenderRegion region, Collection<ChunkBuildOutput> results) {
        // shadowed: the body is the installed method's
    }

    @Shadow
    private void uploadResorts(CommandList commandList, RenderRegion region, Collection<ChunkBuildOutput> results) {
        // shadowed: the body is the installed method's
    }

    /**
     * @author BonsUnleashed
     * @reason Split each region's outputs with ac$classify instead of two stream pipelines per region.
     */
    @Overwrite
    public void uploadMeshes(CommandList commandList, Collection<ChunkBuildOutput> results) {
        for (Reference2ReferenceMap.Entry<RenderRegion, List<ChunkBuildOutput>> entry : this.createMeshUploadQueues(results)) {
            this.uploadMeshes(commandList, entry.getKey(), ac$classify(entry.getValue(), o -> !o.isIndexOnlyUpload()));
            this.uploadResorts(commandList, entry.getKey(), ac$classify(entry.getValue(), ChunkBuildOutput::isIndexOnlyUpload));
        }
    }

    /** outputs.stream().filter(filter).toList(), without the stream for Embeddium's own ArrayList of outputs. */
    @Unique
    private static List<ChunkBuildOutput> ac$classify(List<ChunkBuildOutput> outputs, Predicate<ChunkBuildOutput> filter) {
        if (outputs.getClass() != ArrayList.class) {
            return outputs.stream().filter(filter).toList();
        }
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i) == null || outputs.get(i).getClass() != ChunkBuildOutput.class) {
                return outputs.stream().filter(filter).toList();
            }
        }
        ArrayList<ChunkBuildOutput> selected = null;
        for (int i = 0; i < outputs.size(); i++) {
            ChunkBuildOutput output = outputs.get(i);
            if (filter.test(output)) {
                if (selected == null) {
                    selected = new ArrayList<>();
                }
                selected.add(output);
            }
        }
        return selected == null ? Collections.emptyList() : Collections.unmodifiableList(selected);
    }
}
