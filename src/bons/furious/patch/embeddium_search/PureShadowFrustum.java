package bons.furious.patch.embeddium_search;

/**
 * Bons and Furious switch oculus_shadow_search_replay (Oculus 1.8.0, client only): marker interface that
 * bons.furious.mixin.embeddium_search.ShadowFrustumMarkerMixin adds to Oculus's shadow-pass frusta when their tested
 * code is installed (the switch's guard fingerprints AdvancedShadowCullingFrustum.checkCornerVisibility, BoxCuller and
 * the testAab methods Oculus adds for Embeddium). Their testAab only reads the frustum's own fields, so a bit-for-bit copy
 * of those fields (SearchReplay.frustumPlan, SearchReplay.key) is everything a shadow-pass search reads from its frustum,
 * and embeddium_search_replay may key and replay such searches (1.0.34: this text named SearchReplay.frustumOk, which
 * does not exist, and said recorded sections are re-tested; the replay never re-tests them). Without the marker (Oculus
 * absent, another Oculus build, the switch off) shadow-pass searches simply run unchanged.
 */
public interface PureShadowFrustum {
}
