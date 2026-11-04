package bons.furious.patch.iceandfire_c49;

/**
 * iceandfire_entity_data_memo (Ice and Fire 2.1.13-1.20.1-beta-5, both sides, since 1.0.49): the one field
 * bons.furious.mixin.iceandfire_c49.LivingEntityDataMemoMixin adds to LivingEntity, the memo of the last answer Ice and
 * Fire's EntityDataProvider.getCapability(Entity) gave for this entity from its side map (EntityDataMemo.Memo). Ours; no
 * Minecraft or Ice and Fire code.
 */
public interface EntityDataMemoHolder {
    EntityDataMemo.Memo bons$iafDataMemo();

    void bons$iafDataMemo(EntityDataMemo.Memo memo);
}
