package bons.furious.patch.titanium_coo49;

import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch titanium_progress_same_value_skip (since 1.0.49; Titanium 4.0.52 for NeoForge 1.21.1, server side
 * in effect; kind change, off by default; helper of ProgressBarSameValueMixin; no Titanium code is carried).
 *
 * What Titanium does. ProgressBarComponent.setProgress(value) stores the value and then always calls
 * markComponentForUpdate(true) on the machine, which marks the block entity changed (ActiveTile.markComponentDirty ->
 * BlockEntity.setChanged): the chunk is flagged unsaved, the comparator neighbours are updated and every override the
 * machine has of the changed notification runs (Industrial Foregoing's Resourceful Furnace and Dissolution Chamber look
 * their recipes up again there). Idle machines write the same progress (usually 0) every tick, so all of that runs every
 * tick for nothing.
 *
 * What the switch does (trade-off, off by default). A write of the value the bar already holds returns at once, without
 * the changed notification. The progress, the bar, the screen data and comparator readings are the same; what differs: a
 * same-value write no longer marks the machine changed, so its chunk is not flagged unsaved by it, neighbours are not
 * notified and the machine's own changed-hook (recipe re-checks) does not run for it. Inventory and fluid changes still mark
 * the machine changed themselves. The properties switch decides; runtime flag bons_and_furious.titaniumProgressSameValueSkip
 * is the emergency off.
 */
public final class TitaniumProgress {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.titaniumProgressSameValueSkip", "true"));
    /** Statistics for probes and the harness (plain field): same-value writes left without the changed notification. */
    public static long skipped;
    private static volatile boolean announced;

    private TitaniumProgress() {
    }

    /** One same-value write was answered without the notification. */
    public static void skipped() {
        skipped++;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: titanium_progress_same_value_skip applies (a Titanium progress bar written with the value it holds no longer marks its machine changed)");
        }
    }
}
