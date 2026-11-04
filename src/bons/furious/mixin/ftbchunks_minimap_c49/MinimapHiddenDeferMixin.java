package bons.furious.mixin.ftbchunks_minimap_c49;

import bons.furious.patch.ftbchunks_minimap_c49.MinimapHiddenDefer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftblibrary.snbt.config.BooleanValue;
import net.minecraft.client.gui.GuiGraphics;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ftbchunks_hidden_minimap_upload_defer (FTB Chunks 2001.3.8, All Rights Reserved; client; exact variant): in
 * FTBChunksClient.renderHud, the rebuild loop's start decides whether the minimap is hidden this frame; while it is, the
 * loop's uploads are kept back (@WrapWithCondition) and sent when the texture can be seen: right after renderHud's own
 * visibility check (the read of MINIMAP_PROPORTIONAL is the first thing it does when it goes on to draw) and in
 * getMinimapTextureId(). See MinimapHiddenDefer; MinimapHiddenReleaseMixin covers region releases.
 */
@Mixin(value = FTBChunksClient.class, remap = false)
public abstract class MinimapHiddenDeferMixin {
    @Shadow
    private int minimapTextureId;

    @Inject(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "FIELD", target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;updateMinimapScheduled:Z", opcode = Opcodes.PUTFIELD), require = 1, allow = 1)
    private void bons$ftbchunksMinimapC49HiddenStart(GuiGraphics graphics, float tickDelta, CallbackInfo ci) {
        MinimapHiddenDefer.loopStart();
    }

    @WrapWithCondition(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;m_85013_(IIIIIIIZZZZ)V"), require = 1, allow = 1)
    private boolean bons$ftbchunksMinimapC49HiddenUpload(NativeImage image, int level, int x, int y, int sx, int sy, int w, int h,
                                                         boolean blur, boolean clamp, boolean mipmap, boolean autoClose) {
        return MinimapHiddenDefer.upload(image, level, x, y, sx, sy, w, h, blur, clamp, mipmap, autoClose);
    }

    @Inject(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "FIELD", target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;currentPlayerChunkX:I", opcode = Opcodes.PUTFIELD), require = 1, allow = 1)
    private void bons$ftbchunksMinimapC49HiddenEnd(GuiGraphics graphics, float tickDelta, CallbackInfo ci) {
        MinimapHiddenDefer.loopEnd(this.minimapTextureId);
    }

    @ModifyExpressionValue(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "FIELD", target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClientConfig;MINIMAP_PROPORTIONAL:Ldev/ftb/mods/ftblibrary/snbt/config/BooleanValue;", opcode = Opcodes.GETSTATIC),
            require = 1, allow = 1)
    private BooleanValue bons$ftbchunksMinimapC49HiddenShown(BooleanValue value) {
        MinimapHiddenDefer.visibleFrame(this.minimapTextureId);
        return value;
    }

    @ModifyReturnValue(method = "getMinimapTextureId()I", at = @At("RETURN"), require = 1, allow = 1)
    private int bons$ftbchunksMinimapC49HiddenReader(int textureId) {
        MinimapHiddenDefer.reader(textureId);
        return textureId;
    }
}
