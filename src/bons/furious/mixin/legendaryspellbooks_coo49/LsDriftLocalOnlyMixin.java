package bons.furious.mixin.legendaryspellbooks_coo49;

import bons.furious.patch.legendaryspellbooks_coo49.LsDriftClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * legendary_spellbooks_drift_local_player_only (Legendary Spellbooks 0.4.1, LGPL-3.0; client; gameplay fix, off by
 * default): MixinExtras @ModifyExpressionValue on the handler's read of PlayerTickEvent.player in
 * FlamebornDriftSpellHandler.onPlayerTick: null for every player but the local one, so the handler's own null test ends it.
 * See LsDriftClient. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "dev.higurashi.legendary_spellbooks.common.effects.handler.FlamebornDriftSpellHandler", remap = false)
public abstract class LsDriftLocalOnlyMixin {
    @ModifyExpressionValue(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;player:Lnet/minecraft/world/entity/player/Player;", ordinal = 0),
            require = 1, allow = 1)
    private static Player bons$legendaryspellbooksCoo49DriftLocalOnly(Player player) {
        return LsDriftClient.localOnly(player);
    }
}
