package bons.furious.patch.skyarena_coo49;

/**
 * skyarena_spawn_scan_cache_ticks (Sky Arena 1.3.6, MIT; server): the altar block entity's slot for its kept spawn-square
 * sweep, added by AltarSpawnScanCacheMixin (a field of the block entity, so the sweep goes with the altar).
 */
public interface SkyarenaCoo49MemoHolder {
    Object bons$skyarenaCoo49Memo();

    void bons$skyarenaCoo49SetMemo(Object memo);
}
