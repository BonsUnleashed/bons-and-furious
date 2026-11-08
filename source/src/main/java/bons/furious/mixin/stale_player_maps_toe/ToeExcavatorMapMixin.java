package bons.furious.mixin.stale_player_maps_toe;

import bons.furious.patch.stale_player_maps_toe.ToeMaps;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * stale_player_map_purge_tonsofenchants (Tons Of Enchants 1.21.1-v1.0 for NeoForge, MIT; server): @Inject at the RETURN of
 * the static initializer of Tons Of Enchants' helper class PU hands its two static player maps (excavatorMap of the
 * Excavator enchantment, orechainMap of Ore Chain) to the shared stale-player-map layer (ToeMaps). Nothing of the class is
 * changed.
 */
@Mixin(targets = "com.deltajay.tonsofenchants.util.PU", remap = false)
public abstract class ToeExcavatorMapMixin {
    @Shadow
    @Final
    private static Map<Player, Boolean> excavatorMap;

    @Shadow
    @Final
    private static Map<Player, Boolean> orechainMap;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void bons$toeTrack(CallbackInfo ci) {
        ToeMaps.track(excavatorMap, orechainMap);
    }
}
