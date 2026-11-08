package bons.furious.patch.oculus_passes;

/**
 * vanilla_order_independent_sort_skip (Bons and Furious 1.0.49; Minecraft 1.21.1 on NeoForge 21.1.252, client):
 * implemented by every RenderType through RenderTypeOrderFreeMixin. bons$orderFreeQuads() says, decided once per render
 * type (SortSkip.classify), whether the draw order of its quads cannot change the picture without a shader pack.
 */
public interface OrderFreeType {
    boolean bons$orderFreeQuads();
}
