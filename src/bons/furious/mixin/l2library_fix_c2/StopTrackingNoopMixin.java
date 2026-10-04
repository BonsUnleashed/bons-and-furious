package bons.furious.mixin.l2library_fix_c2;

import bons.furious.patch.l2library_fix_c2.StopTrackingNoop;
import dev.xkmc.l2library.init.events.EffectSyncEvents;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * l2library_stop_tracking_noop (L2 Library 2.5.1-slim, LGPL-2.1; both sides, acts on the server; a fix): the
 * stop-tracking handler returns at once (see {@link StopTrackingNoop}). It runs once per entity a player stops tracking,
 * so the CallbackInfo of @Inject costs nothing measurable.
 */
@Mixin(value = EffectSyncEvents.class, remap = false)
public abstract class StopTrackingNoopMixin {
    @Inject(method = "onPlayerStopTracking", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$noRemovalToRemainingViewers(PlayerEvent.StopTracking event, CallbackInfo ci) {
        if (StopTrackingNoop.skip()) ci.cancel();
    }
}
