package bons.furious.mixin.brutalbosses_coo49;

import bons.furious.patch.brutalbosses_coo49.BossBarLocal;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * brutalbosses_boss_bar_local_player (Brutal Bosses 8.5, All Rights Reserved; client; gameplay fix, off by default):
 * @Inject at HEAD (cancellable) of ClientEventHandler.onPlayerTick: the boss bar bookkeeping runs only for the local
 * player's end-of-tick event (BossBarLocal) instead of the start and end of every player entity the client ticks. Our own
 * logic only.
 */
@Mixin(targets = "com.brutalbosses.event.ClientEventHandler", remap = false)
public abstract class BossBarLocalPlayerMixin {
    @Inject(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private static void bons$brutalbossesCoo49LocalOnly(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (BossBarLocal.skip(event)) ci.cancel();
    }
}
