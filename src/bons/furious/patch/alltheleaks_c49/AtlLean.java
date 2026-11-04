package bons.furious.patch.alltheleaks_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group alltheleaks_c49 (AllTheLeaks 1.1.3; 1.1.4's classes are byte-identical; MIT): helpers of four
 * switches. Ours; no AllTheLeaks code is carried. Idea: Collections Of Optimizations (alltheleaks.*), idea text only.
 *
 * alltheleaks_dead_ref_hash_spread (exact variant, both sides). AllTheLeaks keeps a weak reference to every chunk, level and
 * player it tracks (every chunk unload adds one) in fastutil open hash sets whose strategy (Trackable$1) hashes a reference
 * by its referent: a dead reference hashes to Objects.hashCode(null) = 0. Every rehash of a set that holds dead references
 * therefore places all of them from slot 0 on, one after the other (quadratic in their number), and every insert whose own
 * slot falls into that run walks to its end (reported as over 100 ms per insert after long travel). With the switch a dead
 * reference hashes to the identity hash of the reference object instead; a live one keeps its referent's hash. Live
 * entries are placed, found and compared exactly as before (their hash is unchanged; strategy equality is untouched: two
 * dead references are still equal, a dead one never equals a live one), so the set's live content, every leak count and
 * every add / contains answer for a live object stay the same. The one internal difference: where dead entries sit, so the
 * ORDER in which a summary meets the live classes of one base class can differ (it also moves at every trim as shipped),
 * and which dead entries a wrapped-iterator removal takes (any dead entry is equal to any other).
 *
 * alltheleaks_unread_summary_skip (exact, both sides). AllTheLeaks rebuilds its cached leak summary (a walk of every
 * tracked reference, grouped by class) every 101 ticks; the cached text is read only by the client debug screen (when
 * showSummaryOnDebugScreen is on) and by the passive_memory_leak_report log task. With the switch a rebuild is skipped
 * when nothing can read it before the next rebuild: no debug reader (a dedicated server, or a client with the setting
 * off) and the log task's next run is strictly after the next rebuild (both read from the same ReportManager tick
 * counter and intervals; a log run in the same tick as the next rebuild counts as a reader, whatever the task order).
 * The task's own bookkeeping (lastTickReported) is untouched, so every later run happens at the same tick as shipped, and
 * every summary the log task writes was built at the same tick as shipped. force_refresh, the memory warning and crash
 * reports build their own summary and are unaffected.
 *
 * alltheleaks_crash_report_gc_skip (change, off by default) and alltheleaks_world_close_gc_background (change, off by
 * default): see their methods.
 *
 * Runtime flags: -Dbons_and_furious.alltheleaksDeadRefHashSpread, .alltheleaksUnreadSummarySkip,
 * .alltheleaksCrashReportGcSkip, .alltheleaksWorldCloseGcBackground (=false: AllTheLeaks' own path).
 * Shadow (unread summary): -Dbons_and_furious.alltheleaksUnreadSummarySkip.shadow=true runs every rebuild and counts the
 * skippable ones (SHADOW_SKIPPABLE); SHADOW_CHECKS / SHADOW_MISMATCHES count log reads that would have seen a skipped build.
 */
public final class AtlLean {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean hashSpread = flag("alltheleaksDeadRefHashSpread");
    public static volatile boolean summarySkip = flag("alltheleaksUnreadSummarySkip");
    public static final boolean SUMMARY_SHADOW = Boolean.getBoolean("bons_and_furious.alltheleaksUnreadSummarySkip.shadow");
    public static volatile boolean crashGcSkip = flag("alltheleaksCrashReportGcSkip");
    public static volatile boolean closeGcBackground = flag("alltheleaksWorldCloseGcBackground");
    public static final AtomicLong SKIPPED = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong(),
            SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** For the harness: null = decide from FMLEnvironment, else forces "dedicated server" (true) or "client" (false). */
    public static volatile Boolean testDedicated;
    /** For the harness: the last world-close thread. */
    public static volatile Thread lastCloseThread;
    static final String UPDATE = "update_leak_summary", LOG = "passive_memory_leak_report";
    private static volatile boolean summaryAnnounced, crashAnnounced, closeAnnounced;
    private static final ThreadLocal<Boolean> DEFERRED = new ThreadLocal<>();
    /** Shadow: the tick of the newest build a skipped rebuild would have left in place, -1 = none. */
    private static volatile int shadowStaleSince = -1;

    private AtlLean() {
    }

    private static boolean flag(String camel) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camel, "true"));
    }

    // ---- alltheleaks_dead_ref_hash_spread ------------------------------------------------------------------------

    /** Trackable$1.hashCode(WeakReference) RETURN: a dead reference hashes by its own identity instead of 0. */
    public static int spreadDead(int hash, WeakReference<?> ref) {
        if (hash != 0 || !hashSpread || ref == null || ref.get() != null) return hash;
        return System.identityHashCode(ref);
    }

    // ---- alltheleaks_unread_summary_skip ---------------------------------------------------------------------------

    /** ReportManager's static final task map, handed over at the end of its class initializer (ReportManagerTasksMixin). */
    private static volatile Map<String, ?> tasks;

    public static void captureTasks(Map<String, ?> map) {
        tasks = map;
    }

    /** The task map, or null before ReportManager is initialized (unread() then answers "keep the rebuild"). */
    public static Map<String, ?> tasks() {
        return tasks;
    }

    static boolean debugReader() {
        boolean dedicated;
        Boolean t = testDedicated;
        if (t != null) dedicated = t;
        else {
            try {
                dedicated = net.minecraftforge.fml.loading.FMLEnvironment.dist != null && net.minecraftforge.fml.loading.FMLEnvironment.dist.isDedicatedServer();
            } catch (Throwable e) {
                return true;
            }
        }
        if (dedicated) return false;
        return dev.uncandango.alltheleaks.config.ATLProperties.get().showSummaryOnDebugScreen;
    }

    /** May the rebuild that is about to run at this tick be left out? */
    static boolean unread(ReportTaskView update, Map<String, ?> tasks, int now) {
        if (debugReader()) return false;
        Object log = tasks.get(LOG);
        if (!(log instanceof ReportTaskView g) || g.bons$atlStopped()) return true;     // nothing reads the cache any more
        int dueIn = g.bons$atlLastRun() + g.bons$atlInterval() + 1 - now;              // ticks until the log task runs
        if (dueIn < 0) dueIn = 0;
        return update.bons$atlInterval() + 1 < dueIn;                                  // the next rebuild comes first
    }

    /** ReportManager$ReportTask.tick: the task's Runnable.run call. */
    public static void runTask(ReportTaskView self, Runnable task, Map<String, ?> tasks, int now, Operation<Void> original) {
        if (!summarySkip || !UPDATE.equals(self.bons$atlName())) {
            if (SUMMARY_SHADOW && summarySkip && LOG.equals(self.bons$atlName())) {
                SHADOW_CHECKS.incrementAndGet();
                if (shadowStaleSince >= 0) {
                    long m = SHADOW_MISMATCHES.incrementAndGet();
                    if (m <= 20) LOGGER.warn("Bons and Furious: alltheleaks_unread_summary_skip shadow mismatch #{}: the log task at tick {} would have read a skipped rebuild", m, now);
                }
            }
            original.call(task);
            return;
        }
        boolean skip;
        try {
            skip = unread(self, tasks, now);
        } catch (Throwable e) {
            skip = false;
        }
        if (SUMMARY_SHADOW) {
            if (skip) {
                SHADOW_SKIPPABLE.incrementAndGet();
                if (shadowStaleSince < 0) shadowStaleSince = now;
            } else {
                shadowStaleSince = -1;
            }
            original.call(task);
            return;
        }
        if (!skip) {
            original.call(task);
            return;
        }
        SKIPPED.incrementAndGet();
        if (!summaryAnnounced) {
            summaryAnnounced = true;
            LOGGER.info("Bons and Furious: alltheleaks_unread_summary_skip applies (AllTheLeaks' leak summary is only rebuilt when something can read it)");
        }
    }

    // ---- alltheleaks_crash_report_gc_skip (change, off by default) ---------------------------------------------------

    /**
     * ModEvent.generateReportForCrashReport: AllTheLeaks forces a full garbage collection (System.gc) on every second crash
     * report so that its leak list counts only objects that are really alive. Trade-off: not called; the leak list in the
     * crash report can count objects that were about to be collected, and "Explicit GC Last Run" shows the older time. The
     * rest of the section, every count and the report counter are as shipped.
     */
    public static boolean crashReportGc(Operation<Boolean> original) {
        if (!crashGcSkip) return original.call();
        if (!crashAnnounced) {
            crashAnnounced = true;
            LOGGER.info("Bons and Furious: alltheleaks_crash_report_gc_skip applies (crash reports no longer force a full garbage collection)");
        }
        return false;
    }

    // ---- alltheleaks_world_close_gc_background (change, off by default) ----------------------------------------------

    /**
     * GameBusEvent.onClosedServer (ServerStoppedEvent): AllTheLeaks forces a full garbage collection and then drops its dead
     * references, on the thread that stops the server (in single player, leaving the world waits for it). Trade-off: both
     * run, in the same order and under AllTheLeaks' own lock, on one daemon thread; the stop statistic is still counted on
     * the calling thread. For the second or so the collection takes, a summary or force_refresh can still count objects it
     * is clearing.
     */
    public static boolean worldCloseGc(Operation<Boolean> original) {
        if (!closeGcBackground) return original.call();
        Thread t = new Thread(() -> {
            try {
                original.call();
                dev.uncandango.alltheleaks.mixin.Trackable.clearNullReferences();
            } catch (Throwable e) {
                LOGGER.warn("Bons and Furious: alltheleaks_world_close_gc_background: the background collection failed ({})", e.toString());
            }
        }, "Bons and Furious AllTheLeaks world-close GC");
        t.setDaemon(true);
        DEFERRED.set(Boolean.TRUE);
        lastCloseThread = t;
        t.start();
        if (!closeAnnounced) {
            closeAnnounced = true;
            LOGGER.info("Bons and Furious: alltheleaks_world_close_gc_background applies (the world-close garbage collection runs in the background)");
        }
        return true;
    }

    /** GameBusEvent.onClosedServer's clearNullReferences call: done by the background thread when it was started here. */
    public static void worldCloseClear(Operation<Void> original) {
        if (DEFERRED.get() == Boolean.TRUE) {
            DEFERRED.remove();
            return;
        }
        original.call();
    }
}
