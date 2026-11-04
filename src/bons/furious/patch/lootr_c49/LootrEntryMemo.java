package bons.furious.patch.lootr_c49;

/**
 * lootr_ticker_blocker_memo (Lootr 0.7.35.94): the per-entry memo our mixin adds to TileTicker$Entry: which of Lootr's own
 * chunk tests failed for this waiting chest (none, a hasChunk miss of chunk x/z, or a LOADED_CHUNKS miss of a chunk
 * position of the entry's own set), and whether Lootr's own full evaluation already ran for it once.
 */
public interface LootrEntryMemo {
    int bons$lootrC49Kind();

    int bons$lootrC49X();

    int bons$lootrC49Z();

    Object bons$lootrC49Pos();

    void bons$lootrC49Remember(int kind, int x, int z, Object pos);

    boolean bons$lootrC49Seen();

    void bons$lootrC49SetSeen(boolean seen);
}
