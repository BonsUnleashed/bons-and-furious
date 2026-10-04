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
 * oculus_shadow_outline_discard (Oculus 1.8.0 for Minecraft 1.20.1 / Forge 47.4.16, client only). Fix.
 *
 * ShadowRenderer.renderShadows opens a level-rendering pass on its own RenderBuffers (beginLevelRendering), renders the
 * shadow entities and block entities, ends the entity batch, draws translucent terrain and finally closes the pass with
 * RenderBuffersExt.endLevelRendering(). Nothing in between ends the outline batch of those buffers, which armor renderers
 * such as GeckoLib's GeoArmorRenderer fill for glowing wearers (see ShadowOutlineDiscard). This wraps that closing call:
 * just before it, while the pass is still open (so RenderBuffers.outlineBufferSource() still answers Oculus's per-buffers
 * source), ShadowOutlineDiscard.discard() ends the pending outline batch without drawing it; then the call runs as shipped.
 * Oculus is LGPL-3.0; nothing of its code is carried.
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
