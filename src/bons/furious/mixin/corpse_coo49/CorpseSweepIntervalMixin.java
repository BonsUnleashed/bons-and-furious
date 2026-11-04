package bons.furious.mixin.corpse_coo49;

import bons.furious.patch.corpse_coo49.CorpseSweeps;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * corpse_death_sweep_interval (change, off by default; Corpse 1.20.1-1.0.23, DeathEvents.deleteOldDeaths; server). At the
 * start of the method a sweep request made within the configured number of seconds of the last accepted request of the
 * same server is dropped (CorpseSweeps.dropByInterval); every other request runs on unchanged (into Corpse's own thread, or
 * into the single worker when corpse_death_sweep_single_worker is on). Trade-off: expired death files can stay on disk
 * until the first death after the interval. No Corpse code is carried.
 */
@Mixin(targets = "de.maxhenkel.corpse.events.DeathEvents", remap = false)
public abstract class CorpseSweepIntervalMixin {
    @Inject(method = "deleteOldDeaths(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$corpseCoo49SweepInterval(ServerLevel level, CallbackInfo ci) {
        if (CorpseSweeps.intervalEnabled && CorpseSweeps.dropByInterval(level)) ci.cancel();
    }
}
