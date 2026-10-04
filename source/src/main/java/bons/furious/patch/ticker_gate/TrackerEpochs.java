package bons.furious.patch.ticker_gate;

/**
 * vanilla_ticker_range_memo: implemented by TickingTracker through bons.furious.mixin.ticker_gate.TickingTrackerEpochsMixin.
 * A write version of the whole tracker and one long epoch per group of 8x8-chunk regions (TickerGate.group), all bumped
 * before and after every write of the tracker's ticking-level map (setLevel; the version for any chunk, an epoch for the
 * chunk's group). The epoch array object also identifies the tracker in a remembered answer.
 */
public interface TrackerEpochs {
    long[] bons$tickerEpochs();

    long bons$tickerVersion();
}
