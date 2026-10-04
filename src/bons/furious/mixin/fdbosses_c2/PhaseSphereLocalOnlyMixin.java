package bons.furious.mixin.fdbosses_c2;

import bons.furious.patch.fdbosses_c2.PhaseSphereLocal;
import com.finderfeed.fdbosses.content.items.chesed.PhaseSphereHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * fdbosses_phase_sphere_local_only (a FIX, client desync; FD Bosses 3.1.0.3, CLIENT only: listed under "client").
 * Wraps PhaseSphereHandler.onChesedItemUse(Player): on a client level, a player other than the local player is left alone
 * (FD Bosses' client branch belongs to the local player's static use state); everything else is the original call.
 * See PhaseSphereLocal.
 */
@Mixin(value = PhaseSphereHandler.class, remap = false)
public abstract class PhaseSphereLocalOnlyMixin {
    @WrapMethod(method = "onChesedItemUse")
    private static void bons$localPlayerOnly(Player player, Operation<Void> original) {
        if (PhaseSphereLocal.enabled && PhaseSphereLocal.otherClientPlayer(player)) return;
        original.call(player);
    }
}
