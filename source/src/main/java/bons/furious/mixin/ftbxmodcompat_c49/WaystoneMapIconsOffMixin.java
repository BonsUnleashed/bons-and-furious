package bons.furious.mixin.ftbxmodcompat_c49;

import bons.furious.patch.ftbxmodcompat_c49.WaystoneMapIconsOff;
import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbxmodcompat.ftbchunks.waystones.WaystonesCommon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ftbxmodcompat_waystone_map_icons_off (FTB XMod Compat 21.1.14, All Rights Reserved; client; kind change, off by default):
 * a cancellable HEAD inject on WaystonesCommon.mapWidgets(MapIconEvent), FTB XMod Compat's listener on FTB Chunks' MINIMAP
 * and LARGE_MAP icon events. What changes is described in WaystoneMapIconsOff.
 */
@Mixin(value = WaystonesCommon.class, remap = false)
public abstract class WaystoneMapIconsOffMixin {
    @Inject(method = "mapWidgets(Ldev/ftb/mods/ftbchunks/api/client/event/MapIconEvent;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$ftbxmodcompatC49NoWaystoneIcons(MapIconEvent event, CallbackInfo ci) {
        if (WaystoneMapIconsOff.skip()) ci.cancel();
    }
}
