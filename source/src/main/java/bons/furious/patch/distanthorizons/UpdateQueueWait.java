package bons.furious.patch.distanthorizons;

import java.util.concurrent.locks.LockSupport;

/**
 * distanthorizons_update_queue_wait (Distant Horizons 3.3.2, both sides): helper for UpdateQueueWaitMixin.
 *
 * Distant Horizons holds every chunk update back for 250 ms (UPDATE_DEBOUNCE_NANOS). Its update-queue thread only
 * slept when both of its queues were empty, so while the closest queued update was still inside that window the
 * thread popped it, found it too young, put it back and went straight round again: a full core of busy work (a
 * priority-queue poll, a linear remove and three insertions per round) for as long as updates were waiting. Now a
 * round that did no work (no pre-update taken, nothing dispatched) parks the thread for about a millisecond before
 * the next round. Same updates, same order, same LOD data; a due update leaves the queue at most about one park
 * (plus the OS timer tick) later than before, against the 250 ms DH waits anyway.
 */
public final class UpdateQueueWait {
    /** -Dbons_and_furious.dhUpdateQueueWait=false brings the busy loop back. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhUpdateQueueWait", "true"));
    /** Park length per idle round; -Dbons_and_furious.dhUpdateQueueParkMicros overrides it (tests). */
    private static final long PARK_NANOS = 1000L * Math.max(50L, Math.min(20_000L,
            Long.getLong("bons_and_furious.dhUpdateQueueParkMicros", 1000L)));
    /** Rounds that parked (for the probe). */
    public static volatile long parkedRounds;

    private UpdateQueueWait() {
    }

    /** Called by the queue thread after a round that took no pre-update and dispatched nothing. */
    public static void idle() {
        parkedRounds++;
        LockSupport.parkNanos(PARK_NANOS);
    }
}
