package bons.furious.mixin.oculus;

import de.odysseus.ithaka.digraph.Digraph;
import de.odysseus.ithaka.digraph.Digraphs;
import de.odysseus.ithaka.digraph.MapDigraph;
import de.odysseus.ithaka.digraph.util.fas.FeedbackArcSet;
import de.odysseus.ithaka.digraph.util.fas.FeedbackArcSetPolicy;
import de.odysseus.ithaka.digraph.util.fas.FeedbackArcSetProvider;
import de.odysseus.ithaka.digraph.util.fas.SimpleFeedbackArcSetProvider;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.irisshaders.batchedentityrendering.impl.TransparencyType;
import net.irisshaders.batchedentityrendering.impl.ordering.GraphTranslucencyRenderOrderManager;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * oculus_empty_transparency_graphs (Oculus 1.8.0 for Minecraft 1.20.1).
 *
 * getRenderOrder runs every frame and asked the feedback arc set provider for an arc set, then topologically sorted,
 * every transparency graph, including the graphs that received no render type this frame. For Oculus' own graph
 * (MapDigraph) and provider (SimpleFeedbackArcSetProvider) an empty graph contributes nothing to the order, so it is
 * skipped; any other graph or provider class is still processed exactly as before.
 */
@Mixin(value = GraphTranslucencyRenderOrderManager.class, remap = false)
public abstract class TransparencyGraphOrderMixin {
    @Shadow @Final private FeedbackArcSetProvider feedbackArcSetProvider;
    @Shadow @Final private EnumMap<TransparencyType, Digraph<RenderType>> types;

    /**
     * @author BonsUnleashed
     * @reason Skip the arc set and sort work for empty transparency graphs, once per frame per graph.
     */
    @Overwrite
    public List<RenderType> getRenderOrder() {
        int layerCount = 0;
        for (Digraph<RenderType> graph : this.types.values()) {
            layerCount += graph.getVertexCount();
        }
        ArrayList<RenderType> allLayers = new ArrayList<>(layerCount);
        for (Digraph<RenderType> graph : this.types.values()) {
            if (graph.getClass() == MapDigraph.class && this.feedbackArcSetProvider.getClass() == SimpleFeedbackArcSetProvider.class
                    && graph.getVertexCount() == 0) {
                continue;
            }
            FeedbackArcSet<RenderType> arcSet = this.feedbackArcSetProvider.getFeedbackArcSet(graph, graph, FeedbackArcSetPolicy.MIN_WEIGHT);
            if (arcSet.getEdgeCount() > 0) {
                for (RenderType source : arcSet.vertices()) {
                    for (RenderType target : arcSet.targets(source)) {
                        graph.remove(source, target);
                    }
                }
            }
            allLayers.addAll(Digraphs.toposort(graph, false));
        }
        return allLayers;
    }
}
