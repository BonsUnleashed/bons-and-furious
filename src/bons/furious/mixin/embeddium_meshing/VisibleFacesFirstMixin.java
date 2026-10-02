package bons.furious.mixin.embeddium_meshing;

import bons.furious.patch.embeddium_meshing.VisibleFacesFirst;
import java.util.List;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * embeddium_visible_faces_first (Embeddium 0.3.31 BlockRenderer, with Fusion 1.3.14+a models; client).
 *
 * renderModel's face loop is {@code quads = getGeometry(ctx, face); if (quads.isEmpty() || !isFaceVisible(ctx, face))
 * continue;}. For an allowlisted model (VisibleFacesFirst.allowlisted) the loop's getGeometry call first asks
 * isFaceVisible and returns an empty list for a hidden face, so its quads are never built; a visible face goes on to the
 * original getGeometry, and the loop's own isFaceVisible call for that face is answered from a one-entry memo instead of
 * being evaluated twice. The memo is cleared by every face-loop getGeometry call before anything else, so it can only
 * answer the isFaceVisible call of the same loop iteration (same context, same face). The null-face getGeometry call
 * (ordinal 1) is untouched. BlockRenderer instances belong to one chunk-build thread each, so the memo fields are
 * thread-confined. See VisibleFacesFirst for why the mesh is identical. With the runtime flag off, or for any other
 * model, the original calls run directly (redirects, not wraps: these two calls run for every face of every block,
 * and a wrapped call's argument array is not reliably removed by the JIT). Embeddium is LGPL-3.0; only two calls are
 * redirected, none of its code copied.
 */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class VisibleFacesFirstMixin {
    @Unique
    private BlockRenderContext bons$memoContext;
    @Unique
    private Direction bons$memoFace;

    @Shadow
    private List<BakedQuad> getGeometry(BlockRenderContext ctx, Direction face) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isFaceVisible(BlockRenderContext ctx, Direction face) {
        throw new AssertionError();
    }

    @Redirect(method = "renderModel", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;getGeometry(Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderContext;Lnet/minecraft/core/Direction;)Ljava/util/List;"))
    private List<BakedQuad> bons$visibleFirst(BlockRenderer self, BlockRenderContext ctx, Direction face) {
        this.bons$memoContext = null;
        if (face != null && VisibleFacesFirst.enabled && VisibleFacesFirst.allowlisted(ctx.model())) {
            if (!this.isFaceVisible(ctx, face)) return List.of();
            this.bons$memoContext = ctx;
            this.bons$memoFace = face;
        }
        return this.getGeometry(ctx, face);
    }

    @Redirect(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;isFaceVisible(Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderContext;Lnet/minecraft/core/Direction;)Z"))
    private boolean bons$rememberedVisible(BlockRenderer self, BlockRenderContext ctx, Direction face) {
        if (this.bons$memoContext == ctx && this.bons$memoFace == face) {
            this.bons$memoContext = null;
            return true;
        }
        return this.isFaceVisible(ctx, face);
    }
}
