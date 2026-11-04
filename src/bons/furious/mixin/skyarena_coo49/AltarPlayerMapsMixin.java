package bons.furious.mixin.skyarena_coo49;

import bons.furious.patch.skyarena_coo49.SkyarenaAltarMaps;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * stale_player_map_purge_skyarena (Sky Arena 1.3.6, MIT; server): @Inject at the RETURN of AltarBlockEntity's static
 * initializer hands its two static player maps (activeAltarBlocks, playerMessageTimestamps) to the shared stale-player-map
 * layer (SkyarenaAltarMaps). Nothing of the class is changed.
 */
@Mixin(targets = "net.jrdemiurge.skyarena.block.entity.AltarBlockEntity", remap = false)
public abstract class AltarPlayerMapsMixin {
    @Shadow
    @Final
    private static Map<Player, BlockPos> activeAltarBlocks;
    @Shadow
    @Final
    private static Map<Player, Long> playerMessageTimestamps;

    @Inject(method = "<clinit>", at = @At("RETURN"), require = 1)
    private static void bons$skyarenaCoo49TrackMaps(CallbackInfo ci) {
        SkyarenaAltarMaps.track(activeAltarBlocks, playerMessageTimestamps);
    }
}
