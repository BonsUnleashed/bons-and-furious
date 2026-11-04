package bons.furious.patch.dungeonsdelight_coo49;

/**
 * dungeonsdelight_tenacity_per_player_interval: the Tenacity interval Dungeons Delight last computed for this entity
 * (0 = none yet). Implemented by LivingEntity through LivingEntityTenacityIntervalMixin; not saved.
 */
public interface TenacityIntervalHolder {
    int bons$tenacityInterval();

    void bons$setTenacityInterval(int interval);
}
