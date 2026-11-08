package bons.furious.mixin.xaeroworldmap_c49;

import bons.furious.patch.xaeroworldmap_c49.MapFramePacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import xaero.map.MapProcessor;

/**
 * xaeroworldmap_upload_headroom (Xaero's World Map 1.47.0 for NeoForge 1.21.1, client, since 1.0.49; change, off by
 * default). @ModifyConstant on the 3000000L upload time floor of MapProcessor.onRenderProcess (its only occurrence): the
 * value instead while the world map screen is not open (see MapFramePacing). No World Map code is carried (All Rights
 * Reserved).
 */
@Mixin(value = MapProcessor.class, remap = false)
public abstract class WmUploadHeadroomMixin {
    @ModifyConstant(method = "onRenderProcess", constant = @Constant(longValue = 3000000L))
    private long bons$xaeroworldmapC49UploadFloor(long original) {
        return MapFramePacing.uploadFloor(original);
    }
}
