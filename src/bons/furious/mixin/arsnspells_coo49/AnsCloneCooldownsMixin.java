package bons.furious.mixin.arsnspells_coo49;

import bons.furious.patch.arsnspells_coo49.AnsCooldowns;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * arsnspells_keep_cooldowns_on_clone (Ars 'n' Spells 3.3.5, GPL-3.0: no code carried; gameplay fix, off by default).
 * ModCapabilityProvider.onPlayerClone: right before its getOriginal().invalidateCaps() the cooldown data is copied to the
 * new player as well (AnsCooldowns.copy).
 */
@Mixin(targets = "com.otectus.arsnspells.data.ModCapabilityProvider", remap = false)
public abstract class AnsCloneCooldownsMixin {
    @Inject(method = "onPlayerClone(Lnet/minecraftforge/event/entity/player/PlayerEvent$Clone;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;invalidateCaps()V"))
    private static void bons$arsnspellsKeepCooldowns(PlayerEvent.Clone event, CallbackInfo ci) {
        AnsCooldowns.copy(event);
    }
}
