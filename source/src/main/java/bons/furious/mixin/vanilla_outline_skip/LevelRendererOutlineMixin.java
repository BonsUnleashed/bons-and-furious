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
 * vanilla_entity_outline_composite_skip (tested build: Minecraft 1.21.1 / NeoForge 21.1.252, client only; first written
 * for Minecraft 1.20.1 / Forge 47.4.16).
 *
 * renderLevel: after each RenderTarget.clear call, if the cleared target is this renderer's entity-outline target
 * (entityTarget), OutlineSkip.afterClear records it (the other six clears there are other targets).
 * doEntityOutline: its entityTarget.blitToScreen call runs as is, except that when OutlineSkip.canSkip says nothing touched
 * the target since that clear, the blit's one draw call is left out (OutlineSkip.skipDraw, read by
 * BufferUploaderOutlineMixin). Everything else of doEntityOutline and blitToScreen - blend state, shader, sampler,
 * viewport, masks, the vertex upload - runs exactly as before. See OutlineSkip for why the screen is identical.
 *
 * Ported to 1.21.1: renderLevel takes (DeltaTracker, boolean, Camera, GameRenderer, LightTexture, Matrix4f, Matrix4f) and
 * still clears entityTarget (when shouldShowEntityOutlines()) before the entities, among the same seven RenderTarget.clear
 * calls; doEntityOutline is unchanged (enableBlend, blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ZERO, ONE),
 * entityTarget.blitToScreen(w, h, false), disableBlend, defaultBlendFunc). Same shadow field, same wrapped calls.
 */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class LevelRendererOutlineMixin {
    @Shadow(remap = false)
    private RenderTarget entityTarget;

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;clear(Z)V"), remap = false)
    private void bons$afterOutlineClear(RenderTarget target, boolean onOsx, Operation<Void> original) {
        original.call(target, onOsx);
        if (target == this.entityTarget && target != null) OutlineSkip.afterClear(target);
    }

    @WrapOperation(method = "doEntityOutline", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(IIZ)V"), remap = false)
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
