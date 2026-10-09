package bons.furious.mixin.item_renderers;

import bons.furious.patch.item_renderers.ReuseReset;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * alexsmobs_item_renderer_reuse (Alex's Mobs 1.22.9, client, render thread): AMItemstackRenderer.renderedEntites (a
 * HashMap of dummy entities by type) and blockedRenderEntities (an ArrayList of types whose dummy failed to build) are
 * empty in a new renderer; renderByItem fills them while it draws an entity item and keeps the entity in a local
 * afterwards. ItemRendererReuse calls bons$resetForReuse before every hand-out of a reused renderer, so each draw
 * starts with both empty and creates (or retries) its entity exactly as with a new renderer. Neither is iterated; at
 * most two entries are added per draw, so a cleared map behaves as a new one.
 */
@Mixin(targets = "com.github.alexthe666.alexsmobs.client.render.AMItemstackRenderer", remap = false)
public abstract class AlexsMobsStackRendererMixin implements ReuseReset {
    @Shadow
    @Final
    private Map<String, Entity> renderedEntites;
    @Shadow
    @Final
    private List<EntityType<?>> blockedRenderEntities;

    @Override
    public void bons$resetForReuse() {
        this.renderedEntites.clear();
        this.blockedRenderEntities.clear();
    }
}
