package bons.furious.mixin.brutalbosses_coo49;

import bons.furious.patch.brutalbosses_coo49.BossBarLocal;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * brutalbosses_boss_bar_local_player (Brutal Bosses, All Rights Reserved; 1.21.1 tested build: Brutal Bosses 1.21.1-8.6;
 * client; gameplay fix, off by default): @Inject at HEAD (cancellable) of ClientEventHandler.onPlayerTick: the boss bar
 * bookkeeping runs only for the local player's event (BossBarLocal) instead of the end of every player entity's tick the
 * client runs. Our own logic only.
 *
 * Ported to 1.21.1: 8.6's handler listens to PlayerTickEvent.Post only (8.5 got both the START and the END post), so on
 * 1.21.1 it runs once per tick per player entity; the body (isClientSide, every fifth game tick, the line-of-sight renewal
 * from the event's player) is the same.
 */
@Mixin(targets = "com.brutalbosses.event.ClientEventHandler", remap = false)
public abstract class BossBarLocalPlayerMixin {
    @Inject(method = "onPlayerTick(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private static void bons$brutalbossesCoo49LocalOnly(PlayerTickEvent.Post event, CallbackInfo ci) {
        if (BossBarLocal.skip(event)) ci.cancel();
    }
}
