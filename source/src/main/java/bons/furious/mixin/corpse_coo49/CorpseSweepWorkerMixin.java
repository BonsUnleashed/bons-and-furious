package bons.furious.mixin.corpse_coo49;

import bons.furious.patch.corpse_coo49.CorpseSweeps;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * corpse_death_sweep_single_worker (Corpse 1.21.1-1.1.22, DeathEvents.deleteOldDeaths; server). Right before Corpse makes
 * its new Thread (after its own maxDeathAge < 0 return), the sweep with Corpse's own level and age (captured from the
 * method's locals: ageInDays in slot 1, ageInMillis in slot 2) goes to CorpseSweeps' single worker and the method returns;
 * see CorpseSweeps for why the files deleted and the log lines are the same. No Corpse code is carried.
 */
@Mixin(targets = "de.maxhenkel.corpse.events.DeathEvents", remap = false)
public abstract class CorpseSweepWorkerMixin {
    @Inject(method = "deleteOldDeaths(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At(value = "NEW", target = "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
            cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, require = 1, allow = 1)
    private static void bons$corpseCoo49SweepWorker(ServerLevel level, CallbackInfo ci, int days, long ageMillis) {
        if (!CorpseSweeps.workerEnabled) return;
        CorpseSweeps.submit(level, ageMillis);
        ci.cancel();
    }
}
