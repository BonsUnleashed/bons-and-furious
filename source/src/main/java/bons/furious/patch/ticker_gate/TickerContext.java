package bons.furious.patch.ticker_gate;

/**
 * vanilla_ticker_range_memo: implemented by Level through bons.furious.mixin.ticker_gate.LevelTickerContextMixin. The
 * block-entity ticker that Level.tickBlockEntities has just taken from its list, and the thread that took it. Set for each
 * ticker as the loop stores it, taken (and cleared) by the first ServerLevel.shouldTickBlocksAt(long) on that same thread,
 * which is the loop's own range check for that ticker.
 */
public interface TickerContext {
    Object bons$tickerContext();

    Thread bons$tickerThread();

    void bons$clearTickerContext();
}
