package bons.furious.patch.adamsarsplus_coo49;

/**
 * adamsarsplus_flame_aura_interval: duck interface the LivingEntity mixin (AapAuraClockMixin) adds; the holder's tick count
 * (Entity.tickCount) from which its next Flame Deity aura search may run (0 = never searched in this life of the entity).
 * The member names carry the group.
 */
public interface AapAuraClock {
    int bons$adamsarsplusAuraNext();

    void bons$adamsarsplusSetAuraNext(int tick);
}
