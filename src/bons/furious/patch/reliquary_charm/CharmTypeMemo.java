package bons.furious.patch.reliquary_charm;

/**
 * Bons and Furious switch reliquary_mob_charm_type_gate (since 1.0.46): the memo EntityTypeCharmMemoMixin adds to every
 * EntityType (one int, 0 until first asked: the MobCharmRegistry version it was computed for, shifted left by one, and in
 * bit 0 whether that type has no charm definition). Only ReliquaryCharmGate reads or writes it.
 */
public interface CharmTypeMemo {
    int bons$charmMemo();

    void bons$charmMemo(int memo);
}
