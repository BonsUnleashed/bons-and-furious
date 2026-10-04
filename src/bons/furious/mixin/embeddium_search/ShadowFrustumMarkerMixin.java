package bons.furious.mixin.embeddium_search;

import bons.furious.patch.embeddium_search.PureShadowFrustum;
import net.irisshaders.iris.shadows.frustum.CullEverythingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum;
import org.spongepowered.asm.mixin.Mixin;

/**
 * oculus_shadow_search_replay (Oculus 1.8.0 with Embeddium 0.3.31; client only): adds the empty marker interface
 * PureShadowFrustum to Oculus's shadow-pass frusta (ReversedAdvancedShadowCullingFrustum inherits it). The switch's
 * guard fingerprints the code their Embeddium test runs (Oculus's testAab mixins, checkCornerVisibility, BoxCuller), which
 * reads only the frustum's own fields; with the marker present, embeddium_search_replay may replay shadow-pass searches.
 * No method of these classes changes.
 */
@Mixin(value = {AdvancedShadowCullingFrustum.class, BoxCullingFrustum.class, NonCullingFrustum.class, CullEverythingFrustum.class}, remap = false)
public abstract class ShadowFrustumMarkerMixin implements PureShadowFrustum {
}
