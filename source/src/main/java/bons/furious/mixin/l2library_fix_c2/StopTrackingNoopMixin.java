package bons.furious.mixin.l2library_fix_c2;

import bons.furious.patch.l2library_fix_c2.StopTrackingNoop;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * l2library_stop_tracking_noop (L2 Library, LGPL-2.1; 1.21.1 tested build: L2 Core 3.0.8+15 inside L2 Library 3.0.8; both
 * sides, acts on the server; a fix): the stop-tracking handler returns at once (see {@link StopTrackingNoop}). It runs
 * once per entity a player stops tracking, so the CallbackInfo of @Inject costs nothing measurable.
 *
 * Ported to 1.21.1: the handler is dev.xkmc.l2core.events.EffectSyncEvents.onPlayerStopTracking (L2 Core); same head
 * injection. Named as a string target: L2 Core is Jar-in-Jar inside L2 Library, so its classes are not on the build's
 * compile classpath (only the top-level target jars are).
 */
@Mixin(targets = "dev.xkmc.l2core.events.EffectSyncEvents", remap = false)
public abstract class StopTrackingNoopMixin {
    @Inject(method = "onPlayerStopTracking", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$noRemovalToRemainingViewers(PlayerEvent.StopTracking event, CallbackInfo ci) {
        if (StopTrackingNoop.skip()) ci.cancel();
    }
}
