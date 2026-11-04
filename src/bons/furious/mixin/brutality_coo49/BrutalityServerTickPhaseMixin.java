package bons.furious.mixin.brutality_coo49;

import bons.furious.patch.brutality_coo49.BrutalityTicks;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * brutality_tick_counter_phase (Brutality 0.8.5, MIT; server tick handler, common code; since 1.0.49; change, gameplay fix,
 * off by default): ServerTickHandler.onServerTick returns at its head unless the event is the END post of the tick, so the
 * server tick counter counts once per tick and its sync packet goes out every 20 ticks. Helper BrutalityTicks.
 */
@Mixin(targets = "net.goo.brutality.event.forge.ServerTickHandler", remap = false)
public abstract class BrutalityServerTickPhaseMixin {
    @Inject(method = "onServerTick(Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$brutalityCoo49ServerPhase(TickEvent.ServerTickEvent event, CallbackInfo ci) {
        if (BrutalityTicks.skip(event.phase)) ci.cancel();
    }
}
