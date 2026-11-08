package bons.furious.patch.adamsarsplus_coo49;

/**
 * adamsarsplus_disruption_clean_purge: duck interface the LivingEntity mixin (AapPurgeTailMixin) adds; marks a Disruption
 * victim whose beneficial effects are removed at the end of its effect tick. The member name carries the group.
 */
public interface AapPurgeTarget {
    void bons$adamsarsplusMarkPurge();
}
