package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtScale;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fallentraits_render_scale_frame_memo (client side; trade-off, off by default): @Inject at the head of Minecraft.tick
 * (m_91398_) clears FtScale's world-render mark, so no client tick ever sees it (also after a render that ended by an
 * exception). Adds no behaviour.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class FtClientTickMixin {
    @Inject(method = "m_91398_()V", at = @At("HEAD"), require = 1, allow = 1)
    private void bons$fallentraitsCoo49ClientTickStart(CallbackInfo ci) {
        FtScale.worldRenderEnd();
    }
}
