package bons.furious.mixin.ftbchunks_minimap_c49;

import bons.furious.patch.ftbchunks_minimap_c49.MinimapHiddenDefer;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ftbchunks_hidden_minimap_upload_defer (FTB Chunks 2101.1.23, All Rights Reserved; client): at the HEAD of
 * MapRegion.releaseMapImage(), before the region image is closed, the minimap uploads kept back from that image (while the
 * minimap was hidden) go out first, so the texture keeps the pixels stock uploaded (see MinimapHiddenDefer).
 */
@Mixin(value = MapRegion.class, remap = false)
public abstract class MinimapHiddenReleaseMixin {
    @Shadow
    private NativeImage renderedMapImage;

    @Inject(method = "releaseMapImage()V", at = @At("HEAD"), require = 1, allow = 1)
    private void bons$ftbchunksMinimapC49HiddenRelease(CallbackInfo ci) {
        MinimapHiddenDefer.released(this.renderedMapImage);
    }
}
