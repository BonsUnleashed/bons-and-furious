package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.NoPackBuffers;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.batchedentityrendering.impl.RenderBuffersExt;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oculus_vanilla_buffers_without_shaders (Iris 1.8.12+mc1.21.1 for NeoForge on Minecraft 1.21.1, client; `change`, off by
 * default): MixinSquared @TargetHandler @WrapOperations inside Iris's batched-entity handlers merged into
 * LevelRenderer.renderLevel(DeltaTracker, boolean, Camera, GameRenderer, LightTexture, Matrix4f, Matrix4f): the
 * RenderBuffersExt.beginLevelRendering() call of batchedentityrendering$beginLevelRender (HEAD) and the
 * endLevelRendering() call of batchedentityrendering$endLevelRender (RETURN). See NoPackBuffers for the decision; the
 * HEAD decision of each renderLevel call is kept on NoPackBuffers.Frames (one per LevelRenderer) until its RETURN, so a
 * left-out begin is always paired with a left-out end. Everything else in those handlers (draw-count reset, the Groupable
 * lookup, the other handlers) runs unchanged and takes its vanilla branch when bufferSource() returns vanilla's
 * BufferSource. Priority 1500 (after Iris's 999); require = 0 and expect = 0: when Iris's handlers are not there
 * nothing is injected. Iris is LGPL-3.0-only: no Iris code is carried; our wrappers only decide whether its two calls
 * run.
 */
@Mixin(value = LevelRenderer.class, priority = 1500, remap = false)
public abstract class BatchedNoPackMixin {
    @Unique
    private final NoPackBuffers.Frames bons$frames = new NoPackBuffers.Frames();

    @TargetHandler(mixin = "net.irisshaders.batchedentityrendering.mixin.MixinLevelRenderer", name = "batchedentityrendering$beginLevelRender")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/batchedentityrendering/impl/RenderBuffersExt;beginLevelRendering()V"))
    private void bons$beginBatching(RenderBuffersExt buffers, Operation<Void> original) {
        if (this.bons$frames.begin()) original.call(buffers);
    }

    @TargetHandler(mixin = "net.irisshaders.batchedentityrendering.mixin.MixinLevelRenderer", name = "batchedentityrendering$endLevelRender")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/batchedentityrendering/impl/RenderBuffersExt;endLevelRendering()V"))
    private void bons$endBatching(RenderBuffersExt buffers, Operation<Void> original) {
        if (this.bons$frames.end()) original.call(buffers);
    }
}
