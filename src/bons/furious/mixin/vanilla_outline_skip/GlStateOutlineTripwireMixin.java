package bons.furious.mixin.vanilla_outline_skip;

import bons.furious.patch.vanilla_outline_skip.OutlineSkip;
import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * vanilla_entity_outline_composite_skip (Minecraft 1.20.1 / Forge 47.4.16, client only): the tripwires. Every framebuffer
 * bind through GlStateManager (RenderTarget.bindWrite / bindRead / copyDepthFrom, the outline render types' output state,
 * PostChain passes, Oculus's own GlFramebuffer binds) passes its id to OutlineSkip.bound, every texture bind its id to
 * OutlineSkip.boundTexture; a bind of the entity-outline framebuffer or of its colour texture after its clear marks the
 * target as written for this frame. The ids are returned unchanged (@ModifyVariable at HEAD, no allocation); the methods
 * run exactly as before.
 */
@Mixin(value = GlStateManager.class, remap = false)
public abstract class GlStateOutlineTripwireMixin {
    @ModifyVariable(method = "_glBindFramebuffer", at = @At("HEAD"), ordinal = 1, argsOnly = true, remap = false)
    private static int bons$framebufferTripwire(int framebuffer) {
        return OutlineSkip.bound(framebuffer);
    }

    @ModifyVariable(method = "_bindTexture", at = @At("HEAD"), argsOnly = true, remap = false)
    private static int bons$textureTripwire(int texture) {
        return OutlineSkip.boundTexture(texture);
    }
}
