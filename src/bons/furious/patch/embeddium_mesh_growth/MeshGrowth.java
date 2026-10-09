package bons.furious.patch.embeddium_mesh_growth;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch embeddium_mesh_buffer_growth_fix (Embeddium 0.3.31 on Minecraft 1.20.1 / Forge 47.4.16, client
 * only). Helper of bons.furious.mixin.embeddium_mesh_growth.ChunkMeshGrowthMixin: the runtime flag, a growth counter the
 * qualification rig reads, and one INFO line at the first growth (with the allocation the original would have made).
 */
public final class MeshGrowth {
    /** Runtime switch; -Dbons_and_furious.embeddiumMeshBufferGrowthFix=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.embeddiumMeshBufferGrowthFix", "true"));
    /** Growth events (chunk-mesh buffers that outgrew 131,072 vertices in one facing) since start. */
    public static final AtomicLong GROWS = new AtomicLong();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private MeshGrowth() {
    }

    public static void grew(int vertexCapacity, int stride) {
        GROWS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: embeddium_mesh_buffer_growth_fix: a chunk-mesh buffer grew to {} vertices: {} MB allocated instead of {} MB",
                    vertexCapacity, (long) vertexCapacity * stride >> 20, (long) vertexCapacity * stride * stride >> 20);
        }
    }
}
