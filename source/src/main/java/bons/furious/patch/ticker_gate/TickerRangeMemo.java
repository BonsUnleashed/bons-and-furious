package bons.furious.patch.ticker_gate;

/**
 * vanilla_ticker_range_memo: implemented by LevelChunk$RebindableTickingBlockEntityWrapper through
 * bons.furious.mixin.ticker_gate.TickerWrapperMemoMixin. The ticker's last block-ticking-range answer: the tracker's epoch
 * table it was taken from (null = never), the chunk it was asked for, the tracker's write version and the chunk's region
 * epoch when the answer was known to be current, and the answer. Read and written only by the thread that runs the level's
 * block-entity loop (TickerGate checks the thread).
 */
public interface TickerRangeMemo {
    long[] bons$rangeEpochs();

    long bons$rangeChunk();

    long bons$rangeVersion();

    long bons$rangeStamp();

    boolean bons$rangeAnswer();

    void bons$rangeRemember(long[] epochs, long chunk, long version, long stamp, boolean answer);

    /** The remembered answer is still current at this tracker version (its region epoch did not move). */
    void bons$rangeRenew(long version);
}
