package bons.furious.mixin.simplyswordsoverhaul_coo49;

import bons.furious.patch.simplyswordsoverhaul_coo49.SsoDash;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * simplyswordsoverhaul_logout_dash_restore (Simply Swords Overhaul 1.0.4 with Simply Swords 1.56.0; MIT: no code of the
 * mod is carried; server side; since 1.0.49). Gameplay fix, off by default. @Inject at RETURN of Scheduler's static
 * initialiser (run in Simply Swords Overhaul's mod constructor): SsoDash registers its PlayerLoggedOutEvent and
 * ServerStoppedEvent listeners while the mods load, before any dash can start. The dash bookkeeping itself is in
 * SsoWhisperwindDashMixin, SsoStormsEdgeDashMixin and SsoEmberlashDashMixin.
 */
@Mixin(targets = "net.jrdemiurge.simplyswordsoverhaul.scheduler.Scheduler", remap = false)
public abstract class SsoSchedulerDashMixin {
    @Inject(method = "<clinit>", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private static void bons$simplyswordsoverhaulCoo49InstallDash(CallbackInfo ci) {
        SsoDash.install();
    }
}
