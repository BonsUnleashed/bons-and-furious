package bons.furious.patch.item_renderers;

/**
 * Implemented by the reset mixins of the *_item_renderer_reuse switches (Alex's Caves ACItemstackRenderer, Alex's Mobs
 * AMItemstackRenderer): puts a reused renderer's per-draw caches back into the state its constructor leaves them in,
 * before ItemRendererReuse hands it out again. Render thread only.
 */
public interface ReuseReset {
    void bons$resetForReuse();
}
