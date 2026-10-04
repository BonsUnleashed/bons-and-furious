package bons.furious.mixin.cofh;

import cofh.lib.client.renderer.entity.ITranslucentRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * cofh_translucent_renderer_memo, part 2 of 2: the per-entity renderer lookup in CoFH Core's static
 * ITranslucentRenderer.renderTranslucent goes through TranslucentRenderers (see there).
 *
 * The target is a static interface method, and Mixin 0.8.5 allows only @Overwrite in interface mixins, which would carry
 * CoFH's whole method. So this mixin is empty and only marks the switch: when Mixin applies it, the mixin plugin's
 * preApply (Guards.beforeApply) turns the one dispatcher.getRenderer(entity) call of renderTranslucent into a call of
 * TranslucentRenderers.renderer(dispatcher, entity) - what a @Redirect would generate. No CoFH code is carried; the
 * frustum set-up, the loop, the rendering and the final endBatch stay CoFH's own.
 */
@Mixin(value = ITranslucentRenderer.class, remap = false)
public interface TranslucentRendererLookupMixin {
}
