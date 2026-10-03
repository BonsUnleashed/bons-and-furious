package bons.furious.mixin.oculus;

import de.odysseus.ithaka.digraph.Digraph;
import de.odysseus.ithaka.digraph.MapDigraph;
import java.util.EnumMap;
import net.irisshaders.batchedentityrendering.impl.TransparencyType;
import net.irisshaders.batchedentityrendering.impl.ordering.GraphTranslucencyRenderOrderManager;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * oculus_reuse_empty_graphs (Oculus 1.8.0 for Minecraft 1.20.1).
 *
 * reset runs after every frame's render order is resolved. It cleared the map and allocated a new MapDigraph for
 * every transparency type, even for graphs that received no render type. Now an existing graph without vertices is
 * kept, every other type gets a new MapDigraph as before, and the TransparencyType.values() array is cached instead of
 * cloned on every call.
 */
@Mixin(value = GraphTranslucencyRenderOrderManager.class, remap = false)
public abstract class TransparencyGraphResetMixin {
    @Unique
    private static final TransparencyType[] ac$transparencyTypes = TransparencyType.values();

    @Shadow @Final private EnumMap<TransparencyType, Digraph<RenderType>> types;

    /**
     * @author BonsUnleashed
     * @reason Keep empty transparency graphs instead of reallocating one per type every frame.
     */
    @Overwrite
    public void reset() {
        for (TransparencyType type : ac$transparencyTypes) {
            Digraph<RenderType> graph = this.types.get(type);
            if (graph == null || graph.getVertexCount() != 0) {
                this.types.put(type, new MapDigraph<>());
            }
        }
    }
}
