package bons.furious.patch.fusion;

import net.minecraft.client.renderer.RenderType;

/**
 * Bons and Furious switch fusion_chunk_layer_id (Fusion 1.3.14+a, client).
 *
 * Fusion's ChunkRenderTypeHelper answers "is this a chunk layer?" with {@code RENDER_TYPES.contains(type)} (a Guava
 * ImmutableList of RenderType.chunkBufferLayers(), searched with equals) and "which layer index?" with
 * {@code TO_ID.get(type)} (an ImmutableMap from the same list's i-th layer to i + 1). Fusion's per-layer map
 * (ChunkRenderTypeMap) runs both on every get/put/containsKey, once per quad and more while chunks are meshed.
 *
 * Forge 47.4.16's patched RenderType already numbers exactly those layers: a private {@code chunkLayerId} field (-1 by
 * default, final getter) that the RenderType static initializer sets to i for the i-th element of chunkBufferLayers(),
 * and nothing else writes. So:
 *  - contains(type) is true exactly for the five layer objects themselves, because List.contains asks
 *    {@code type.equals(layer)} and the only RenderType classes in the pack that override equals (Oculus'
 *    OuterWrappedRenderType, InnerWrappedRenderType and TaggingRenderTypeWrapper) reject any object of another class
 *    first; a wrapper has its own chunkLayerId (-1). null is not contained in either version.
 *  - TO_ID.get(type) is Integer.valueOf(i + 1) for the i-th layer and null otherwise; Integer.valueOf(1..5) returns the
 *    same cached boxes TO_ID holds.
 * Both answers below are therefore the values the original lookups return, for every argument.
 */
public final class ChunkLayerIds {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.fusionChunkLayerId=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fusionChunkLayerId", "true"));

    private ChunkLayerIds() {
    }

    /** {@code RenderType.chunkBufferLayers().contains(type)}. */
    public static boolean isChunkLayer(Object type) {
        return type instanceof RenderType t && t.getChunkLayerId() >= 0;
    }

    /** Fusion's {@code TO_ID.get(type)}: the layer's index + 1, or null for a type that is not a chunk layer. */
    public static Integer id(Object type) {
        int id = type instanceof RenderType t ? t.getChunkLayerId() : -1;
        return id >= 0 ? Integer.valueOf(id + 1) : null;
    }
}
