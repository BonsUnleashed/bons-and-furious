package bons.furious.patch.benssharks;

/**
 * Ben's Sharks switches (Ben's Sharks 1.3.0): the run-time groups of benssharks_tick_listeners, switched on by
 * {@link SharksSetup} only after every prerequisite checked out at the end of common setup. Until then (and for good when a
 * check fails) Ben's own listeners stay registered and every hook returns at once, so Ben's Sharks runs as shipped.
 * Formerly part of the separate mod Bons Sharks Fix 1.0.0.
 */
public final class SharkState {
    private SharkState() {}

    /** Ben's 62 LivingTickEvent listeners replaced by per-shark dispatch. */
    public static volatile boolean LIVING;
    /** Ben's 6 LevelTickEvent listeners replaced by tracked per-level sets. */
    public static volatile boolean LEVEL;
}
