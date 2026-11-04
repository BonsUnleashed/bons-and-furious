package bons.furious.mixin.xaeroworldmap_gpu_c49;

import bons.furious.patch.xaeroworldmap_gpu_c49.VramPoll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapLimiter;

/**
 * xaeroworldmap_vram_poll_interval (Xaero's World Map 1.47.0, client, since 1.0.49; change, off by default). Cancellable
 * @Inject at the head of MapLimiter.updateAvailableVRAM: with a known NVIDIA / AMD driver type the query is left out while
 * the last one ran less than the value ago (see VramPoll). Driver detection (type -1) and drivers without a query (type 2)
 * run as shipped. No World Map code is carried (All Rights Reserved).
 */
@Mixin(value = MapLimiter.class, remap = false)
public abstract class WmVramPollMixin {
    @Shadow
    private int driverType;

    @Unique
    private long bons$xaeroworldmapGpuC49LastQuery;

    @Unique
    private boolean bons$xaeroworldmapGpuC49Queried;

    @Inject(method = "updateAvailableVRAM", at = @At("HEAD"), cancellable = true)
    private void bons$xaeroworldmapGpuC49VramPoll(CallbackInfo ci) {
        if (!VramPoll.enabled || (this.driverType != 0 && this.driverType != 1)) return;
        long now = System.nanoTime();
        if (VramPoll.skip(this.bons$xaeroworldmapGpuC49LastQuery, this.bons$xaeroworldmapGpuC49Queried, now)) {
            ci.cancel();
            return;
        }
        this.bons$xaeroworldmapGpuC49Queried = true;
        this.bons$xaeroworldmapGpuC49LastQuery = now;
    }
}
