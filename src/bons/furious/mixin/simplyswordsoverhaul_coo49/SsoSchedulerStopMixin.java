package bons.furious.mixin.simplyswordsoverhaul_coo49;

import bons.furious.patch.simplyswordsoverhaul_coo49.SsoStop;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * simplyswordsoverhaul_server_stop_reset (Simply Swords Overhaul 1.0.4 with Simply Swords 1.56.0; MIT: no code of the mod
 * is carried; server side; since 1.0.49). Fix. @Inject at RETURN of Scheduler's static initialiser (it runs in Simply
 * Swords Overhaul's mod constructor, which builds a Scheduler): SsoStop keeps the scheduler's task list and registers a
 * ServerStoppedEvent listener that empties it, with the two per-player maps Simply Swords Overhaul's sword mixins add
 * (Stars Edge's saved recall spots, Whisperwind's dash hit lists).
 */
@Mixin(targets = "net.jrdemiurge.simplyswordsoverhaul.scheduler.Scheduler", remap = false)
public abstract class SsoSchedulerStopMixin {
    @Shadow(remap = false)
    @Final
    private static List<?> tasks;

    @Inject(method = "<clinit>", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private static void bons$simplyswordsoverhaulCoo49InstallStop(CallbackInfo ci) {
        SsoStop.install(tasks);
    }
}
