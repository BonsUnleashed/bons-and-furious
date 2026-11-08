package bons.furious.patch.subtleeffects_drain_c49;

/**
 * subtleeffects_ticker_removal_drain (Subtle Effects 1.15.1 for NeoForge 1.21.1): a per-object counter our mixin adds to
 * Subtle Effects' Ticker (TickerDrainCountMixin). TickerDrain raises it once per REMOVE_QUEUE entry of that ticker, the
 * one-pass removal takes it down once per occurrence it drops, and it is back at zero before the pass returns. No Subtle
 * Effects field or method is read or changed through it.
 */
public interface SeTickerFlag {
    /** One more queued removal of this ticker. */
    void bons$seDrainC49Queue();

    /** True (and one queued removal fewer) when a queued removal of this ticker is still open. */
    boolean bons$seDrainC49Take();

    /** Back to zero. */
    void bons$seDrainC49Clear();
}
