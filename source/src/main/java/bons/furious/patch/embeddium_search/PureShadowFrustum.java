package bons.furious.patch.embeddium_search;

/**
 * Bons and Furious switch oculus_shadow_search_replay (Oculus 1.8.0, client only): marker interface that
 * bons.furious.mixin.embeddium_search.ShadowFrustumMarkerMixin adds to Oculus's shadow-pass frusta when their tested
 * code is installed (the switch's guard fingerprints AdvancedShadowCullingFrustum.checkCornerVisibility, BoxCuller and
 * the testAab methods Oculus adds for Embeddium). Their testAab only reads the frustum's own fields, so
 * embeddium_search_replay may re-run it on recorded sections (SearchReplay.frustumOk). Without the marker (Oculus absent,
 * another Oculus build, the switch off) shadow-pass searches simply run unchanged.
 *
 * 1.21.1 port: oculus_shadow_search_replay is not ported (TARGET UNAVAILABLE): Oculus has no 1.21.1 build and Iris
 * 1.8.12 declares Embeddium incompatible, so no frustum ever carries this marker (ShadowFrustumMarkerMixin is not in the
 * 1.21.1 tree). The interface stays only because SearchReplay.frustumPlan still tests for it; every 1.21.1 search with
 * a frustum other than Embeddium's SimpleFrustum runs unchanged.
 */
public interface PureShadowFrustum {
}
