package bons.furious.mixin.vanilla_outline_skip;

import bons.furious.patch.vanilla_outline_skip.OutlineSkip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_entity_outline_composite_skip (Minecraft 1.20.1 / Forge 47.4.16, client only).
 *
 * renderLevel (m_109599_): after each RenderTarget.clear call, if the cleared target is this renderer's entity-outline
 * target (entityTarget, f_109411_), OutlineSkip.afterClear records it (the other two clears there are other targets).
 * doEntityOutline (m_109769_): its entityTarget.blitToScreen call runs as is, except that when OutlineSkip.canSkip says
 * nothing touched the target since that clear, the blit's one draw call is left out (OutlineSkip.skipDraw, read by
 * BufferUploaderOutlineMixin). Everything else of doEntityOutline and blitToScreen - blend state, shader, sampler,
 * matrices, viewport, masks, the vertex upload - runs exactly as before. See OutlineSkip for why the screen is identical.
 */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class LevelRendererOutlineMixin {
    @Shadow(remap = false)
    private RenderTarget f_109411_;

    @WrapOperation(method = "m_109599_", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;m_83954_(Z)V"), remap = false)
    private void bons$afterOutlineClear(RenderTarget target, boolean onOsx, Operation<Void> original) {
        original.call(target, onOsx);
        if (target == this.f_109411_ && target != null) OutlineSkip.afterClear(target);
    }

    @WrapOperation(method = "m_109769_", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;m_83957_(IIZ)V"), remap = false)
    private void bons$outlineComposite(RenderTarget target, int width, int height, boolean disableBlend, Operation<Void> original) {
        boolean skip = OutlineSkip.canSkip(target);
        if (OutlineSkip.TIMING) OutlineSkip.timingBegin();
        if (skip) {
            OutlineSkip.skipDraw = true;
            try {
                original.call(target, width, height, disableBlend);
            } finally {
                OutlineSkip.skipDraw = false;
            }
        } else {
            original.call(target, width, height, disableBlend);
        }
        if (OutlineSkip.TIMING) OutlineSkip.timingEnd();
    }
}
