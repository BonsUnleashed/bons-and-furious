package bons.furious.mixin.vanilla_outline_skip;

import bons.furious.patch.vanilla_outline_skip.OutlineSkip;
import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * vanilla_entity_outline_composite_skip (tested build: Minecraft 1.21.1 / NeoForge 21.1.252, client only; first written
 * for Minecraft 1.20.1 / Forge 47.4.16): the tripwires. Every framebuffer bind through GlStateManager
 * (RenderTarget.bindWrite / copyDepthFrom / unbindWrite, the outline render types' output state, PostChain passes, Iris's
 * and other mods' GlStateManager binds) passes its id to OutlineSkip.bound, every texture bind its id to
 * OutlineSkip.boundTexture; a bind of the entity-outline framebuffer or of its colour texture after its clear marks the
 * target as written for this frame. The ids are returned unchanged (@ModifyVariable at HEAD, no allocation); the methods
 * run exactly as before.
 *
 * Ported to 1.21.1: GlStateManager._glBindFramebuffer(int target, int framebuffer) and _bindTexture(int) are unchanged.
 * Iris 1.8.12 cancels redundant framebuffer binds at the same HEAD (MixinGlStateManager_FramebufferBinding); a cancelled
 * bind repeats a framebuffer that the last non-cancelled bind of that slot already bound, and the outline target's clear
 * ends with unbindWrite (framebuffer 0), so the first bind of the outline framebuffer after the clear always reaches this
 * tripwire whichever handler runs first.
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
