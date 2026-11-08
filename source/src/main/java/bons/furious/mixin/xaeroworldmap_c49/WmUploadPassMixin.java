package bons.furious.mixin.xaeroworldmap_c49;

import bons.furious.patch.xaeroworldmap_c49.MapFramePacing;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.map.MapProcessor;

/**
 * xaeroworldmap_upload_pass_interval (Xaero's World Map 1.47.0 for NeoForge 1.21.1, client, since 1.0.49; change, off by
 * default). MixinExtras @ModifyExpressionValue on onRenderProcess's read of pauseUploading in the upload pass test (its
 * first read): read as non-zero (the pass is left out) outside the world map screen while the last pass started less than
 * the value ago (see MapFramePacing). No World Map code is carried (All Rights Reserved).
 */
@Mixin(value = MapProcessor.class, remap = false)
public abstract class WmUploadPassMixin {
    @ModifyExpressionValue(method = "onRenderProcess", at = @At(value = "FIELD", target = "Lxaero/map/MapProcessor;pauseUploading:I", ordinal = 0))
    private int bons$xaeroworldmapC49PassGate(int pauseUploading) {
        return MapFramePacing.passGate(pauseUploading);
    }
}
