package bons.furious.mixin.oculus_outline;

import bons.furious.patch.oculus_outline.ShadowOutlineDiscard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.batchedentityrendering.impl.RenderBuffersExt;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oculus_shadow_outline_discard (Iris 1.8.12+mc1.21.1 for NeoForge, with Sodium 0.6.13, on Minecraft 1.21.1 / NeoForge
 * 21.1.252; client only; first written for Oculus 1.8.0 on 1.20.1). Fix.
 *
 * ShadowRenderer.renderShadows opens a level-rendering pass on its own RenderBuffers (beginLevelRendering), renders the
 * shadow entities and block entities, ends the entity batch, draws translucent terrain and finally closes the pass with
 * RenderBuffersExt.endLevelRendering(). Nothing in between ends the outline batch of those buffers, which armor renderers
 * such as GeckoLib's GeoArmorRenderer fill for glowing wearers (see ShadowOutlineDiscard). This wraps that closing call:
 * just before it, while the pass is still open (so RenderBuffers.outlineBufferSource() still answers Iris's per-buffers
 * source), ShadowOutlineDiscard.discard() ends the pending outline batch without drawing it; then the call runs as shipped.
 *
 * Ported to 1.21.1: Iris 1.8.12's renderShadows has the same structure as Oculus 1.8.0's (setRenderBuffers(this.buffers),
 * beginLevelRendering, entities, block entities, bufferSource.endBatch(), translucent terrain, endLevelRendering, restore;
 * still no endOutlineBatch) and its MixinRenderBuffers still answers its own OutlineBufferSource while a pass is open; same
 * descriptor, same field. Only the helper's vanilla discard changed (1.21 buffer API). Iris is LGPL-3.0; nothing of its
 * code is carried.
 */
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class ShadowOutlineDiscardMixin {
    @Shadow
    @Final
    private RenderBuffers buffers;

    @WrapOperation(method = "renderShadows", at = @At(value = "INVOKE", target = "Lnet/irisshaders/batchedentityrendering/impl/RenderBuffersExt;endLevelRendering()V"))
    private void bons$discardShadowOutline(RenderBuffersExt ext, Operation<Void> original) {
        if (ShadowOutlineDiscard.enabled) ShadowOutlineDiscard.discard(this.buffers);
        original.call(ext);
    }
}
