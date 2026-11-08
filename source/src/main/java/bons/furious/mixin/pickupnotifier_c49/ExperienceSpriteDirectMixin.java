package bons.furious.mixin.pickupnotifier_c49;

import bons.furious.patch.pickupnotifier_c49.SpriteDirect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * pickupnotifier_opaque_sprite_direct (Pick Up Notifier 21.1.1 for Minecraft 1.21.1 / NeoForge, MPL-2.0; client side;
 * trade-off, off by default): ExperienceDisplayEntry.renderSprite at full opacity draws the tinted orb straight into the
 * current framebuffer with blending on (SpriteDirect): the buffer's clear-and-bind and the full-window blit are skipped;
 * fading entries keep it. Targeted by name, so compiling needs no Puzzles Lib classes.
 */
@Mixin(targets = "fuzs.pickupnotifier.client.gui.entry.ExperienceDisplayEntry", remap = false)
public abstract class ExperienceSpriteDirectMixin {
    @Inject(method = "renderSprite(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;IIFF)V", at = @At("HEAD"), require = 1, allow = 1)
    private void bons$pickupnotifierC49OrbBegin(Minecraft minecraft, GuiGraphics guiGraphics, int posX, int posY, float scale, float fadeTime, CallbackInfo ci) {
        SpriteDirect.begin(fadeTime);
    }

    @Redirect(method = "renderSprite(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;IIFF)V",
            at = @At(value = "INVOKE", target = "Lfuzs/pickupnotifier/client/util/TransparencyBuffer;prepareExtraFramebuffer()V"), require = 1, allow = 1)
    private void bons$pickupnotifierC49OrbPrepare() {
        SpriteDirect.prepare();
    }

    @Redirect(method = "renderSprite(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;IIFF)V",
            at = @At(value = "INVOKE", target = "Lfuzs/pickupnotifier/client/util/TransparencyBuffer;drawExtraFramebuffer(Lnet/minecraft/client/gui/GuiGraphics;)V"),
            require = 1, allow = 1)
    private void bons$pickupnotifierC49OrbDraw(GuiGraphics guiGraphics) {
        SpriteDirect.draw(guiGraphics);
    }
}
