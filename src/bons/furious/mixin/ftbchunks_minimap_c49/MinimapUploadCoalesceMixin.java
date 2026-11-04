package bons.furious.mixin.ftbchunks_minimap_c49;

import bons.furious.patch.ftbchunks_minimap_c49.MinimapUploadCoalesce;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ftbchunks_minimap_upload_coalesce (FTB Chunks 2001.3.8, All Rights Reserved; client): @WrapOperation of the rebuild
 * loop's NativeImage.upload in FTBChunksClient.renderHud (its only upload). The tiles of one rebuild are uploaded as at
 * most four rectangles plus the last tile, through the wrapped call itself (see MinimapUploadCoalesce).
 */
@Mixin(value = FTBChunksClient.class, remap = false)
public abstract class MinimapUploadCoalesceMixin {
    @WrapOperation(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;m_85013_(IIIIIIIZZZZ)V"), require = 1, allow = 1)
    private void bons$ftbchunksMinimapC49Coalesce(NativeImage image, int level, int x, int y, int sx, int sy, int w, int h,
                                                  boolean blur, boolean clamp, boolean mipmap, boolean autoClose, Operation<Void> original) {
        MinimapUploadCoalesce.upload(image, level, x, y, sx, sy, w, h, blur, clamp, mipmap, autoClose, original);
    }
}
