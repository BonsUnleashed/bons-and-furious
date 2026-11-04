package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.ShadowSkips;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * oculus_shadow_skip_sign_text (Minecraft 1.20.1 SignRenderer, client, hanging signs included (HangingSignRenderer inherits
 * the method); Oculus 1.8.0 tested; `change`, off by default). SignRenderer.renderSignText (m_278841_) splits and draws up
 * to four lines of glyphs per sign side. A cancellable @Inject at its HEAD returns at once while Oculus renders its shadow
 * map (ShadowSkips.SIGN_TEXT): sign text no longer casts letter-shaped shadows; the sign board itself is drawn by
 * renderSignModel and still casts its shadow. Other HEAD hooks of the method in this pack: Amendments' SignRendererMixin
 * amendments$captureFace (remembers the side for its getDarkColor overwrite, which only renderSignText's body calls; its
 * yaw capture is cleared by renderSignWithText's TAIL either way, so a skipped side leaves nothing behind that changes a
 * colour) and, with ImmediatelyFast's experimental sign_text_buffering on, IF's cached-text quad (its handler runs first,
 * draws the cached quad and cancels, so those signs keep their stock shadow). Priority 1500: lands in a replacement of the
 * method by another mod's @Overwrite of default priority instead of stopping the game. Carries no Minecraft code.
 */
@Mixin(value = SignRenderer.class, priority = 1500, remap = false)
public abstract class SignTextShadowMixin {
    @Inject(method = "m_278841_", at = @At("HEAD"), cancellable = true, require = 0)
    private void bons$shadowSignText(BlockPos pos, SignText text, PoseStack pose, MultiBufferSource buffers, int light, int lineHeight,
                                     int lineWidth, boolean front, CallbackInfo ci) {
        if (ShadowSkips.SIGN_TEXT.skip()) ci.cancel();
    }
}
