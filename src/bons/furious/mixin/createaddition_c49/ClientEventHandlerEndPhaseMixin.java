package bons.furious.mixin.createaddition_c49;

import bons.furious.patch.createaddition_c49.CaSoundFixes;
import com.mrh0.createaddition.event.ClientEventHandler;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * createaddition_soundscape_end_phase (Create Crafts &amp; Additions 1.20.1-1.3.3, MIT; client side; off by default): a
 * cancellable HEAD inject on ClientEventHandler.tickSoundscapes that returns for the START phase, so CASoundScapes.tick()
 * runs once per client tick, at END (CaSoundFixes).
 */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class ClientEventHandlerEndPhaseMixin {
    @Inject(method = "tickSoundscapes(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$caC49EndPhase(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (CaSoundFixes.skipPhase(event.phase == TickEvent.Phase.END)) ci.cancel();
    }
}
