package bons.furious.mixin.stale_player_maps;

import bons.furious.patch.stale_player_maps.TreeChopMaps;
import ht.treechop.compat.NoChopRecursion;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * stale_player_map_purge_treechop (TreeChop 0.19.0, MIT; server map, class on both sides). Fix. The static initializer hands
 * lastChopTickByPlayers to our shared layer (see {@link TreeChopMaps}). No TreeChop code is carried.
 */
@Mixin(value = NoChopRecursion.class, remap = false)
public abstract class TreeChopNoChopRecursionMixin {
    @Shadow
    @Final
    private static Map<Player, Long> lastChopTickByPlayers;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void bons$trackPlayerMap(CallbackInfo ci) {
        TreeChopMaps.track(lastChopTickByPlayers);
    }
}
