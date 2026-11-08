package bons.furious.mixin.legendaryspellbooks_coo49;

import bons.furious.patch.legendaryspellbooks_coo49.LsDriftClient;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * legendary_spellbooks_drift_local_player_only (Legendary Spellbooks 0.3.2 for Minecraft 1.21.1, LGPL-3.0; client; gameplay
 * fix, off by default): a cancellable HEAD injection on FlamebornDriftSpellHandler.onPlayerTick (PlayerTickEvent.Post) that
 * returns at once for every client player but Minecraft's local player, so the handler's sweep and its drift particles run
 * once per client tick, around the local player. The handler reads no other state before its sweep, so nothing else is
 * left out. See LsDriftClient. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "dev.higurashi.legendary_spellbooks.common.effects.handler.FlamebornDriftSpellHandler", remap = false)
public abstract class LsDriftLocalOnlyMixin {
    @Inject(method = "onPlayerTick(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private static void bons$legendaryspellbooksCoo49LocalOnly(PlayerTickEvent.Post event, CallbackInfo ci) {
        if (event != null && LsDriftClient.otherPlayer(event.getEntity())) ci.cancel();
    }
}
