package bons.furious.patch.supplementaries_lean;

/**
 * supplementaries_map_tint_memo: one memo slot on every Block (added by bons.furious.mixin.supplementaries_lean
 * .BlockMapTintSlotMixin). Holds an immutable MapTints.Memo (the holder tag set it was computed for and the answer), or
 * null. Plain field: a racing reader sees a complete memo (final fields) or an older one whose tag set no longer matches.
 */
public interface MapTintSlot {
    Object bons$mapTintMemo();

    void bons$setMapTintMemo(Object memo);
}
