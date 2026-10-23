package bons.furious.mixin.stale_player_maps;

import bons.furious.patch.stale_player_maps.CelestisynthMaps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.thecelestialworkshop.celestisynth.api.animation.player.CSAnimator;

/**
 * stale_player_map_purge_celestisynth (Celestisynth 1.3.4, MIT; client). Fix. The static initializer hands the three
 * per-player layer maps to our shared layer (see {@link CelestisynthMaps}). No Celestisynth code is carried.
 */
@Mixin(value = CSAnimator.class, remap = false)
public abstract class CelestisynthAnimatorMixin {
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void bons$trackPlayerMaps(CallbackInfo ci) {
        CelestisynthMaps.track(CSAnimator.animationData, CSAnimator.otherAnimationData, CSAnimator.mirroredAnimationData);
    }
}
