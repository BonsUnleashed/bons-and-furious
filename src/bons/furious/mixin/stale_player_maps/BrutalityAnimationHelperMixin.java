package bons.furious.mixin.stale_player_maps;

import bons.furious.patch.stale_player_maps.BrutalityMaps;
import dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import java.util.HashMap;
import net.goo.brutality.client.player_animation.AnimationHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * stale_player_map_purge_brutality (Brutality 0.8.4, MIT; client). Fix. The static initializer hands the two per-player
 * modifier maps to our shared layer (see {@link BrutalityMaps}). No Brutality code is carried.
 */
@Mixin(value = AnimationHelper.class, remap = false)
public abstract class BrutalityAnimationHelperMixin {
    @Shadow
    @Final
    private static HashMap<Player, SpeedModifier> speedModifierMap;

    @Shadow
    @Final
    private static HashMap<Player, MirrorModifier> mirrorModifierMap;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void bons$trackPlayerMaps(CallbackInfo ci) {
        BrutalityMaps.track(speedModifierMap, mirrorModifierMap);
    }
}
