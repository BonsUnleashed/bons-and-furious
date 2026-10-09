package bons.furious.mixin.item_renderers;

import bons.furious.patch.item_renderers.ReuseReset;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * alexscaves_item_renderer_reuse (Alex's Caves 2.0.2, client, render thread): ACItemstackRenderer.renderedDreadbowArrow
 * is null in a new renderer; renderByItem fills it with a dummy arrow (EntityType.create with the current level) when
 * it draws a loaded dread bow and reads it only within that branch. ItemRendererReuse calls bons$resetForReuse before
 * every hand-out of a reused renderer, so each draw starts with null and creates its arrow exactly as with a new
 * renderer (same entity creation, same id counter step).
 */
@Mixin(targets = "com.github.alexmodguy.alexscaves.client.render.item.ACItemstackRenderer", remap = false)
public abstract class AlexsCavesStackRendererMixin implements ReuseReset {
    @Shadow
    private Entity renderedDreadbowArrow;

    @Override
    public void bons$resetForReuse() {
        this.renderedDreadbowArrow = null;
    }
}
