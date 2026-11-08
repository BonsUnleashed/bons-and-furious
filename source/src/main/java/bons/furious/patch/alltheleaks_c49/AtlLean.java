package bons.furious.patch.alltheleaks_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.WeakReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group alltheleaks_c49 (AllTheLeaks 1.1.14 for Minecraft 1.21.1 / NeoForge; MIT): helpers of three
 * switches. Ours; no AllTheLeaks code is carried. Idea: Collections Of Optimizations (alltheleaks.*), idea text only.
 *
 * alltheleaks_dead_ref_hash_spread (exact variant, both sides). AllTheLeaks keeps a weak reference to every chunk, level and
 * player it tracks (every chunk unload adds one) in fastutil open hash sets whose strategy (Trackable$1) hashes a reference
 * by its referent: a dead reference hashes to Objects.hashCode(null) = 0. Every rehash of a set that holds dead references
 * therefore places all of them from slot 0 on, one after the other (quadratic in their number), and every insert whose own
 * slot falls into that run walks to its end. With the switch a dead reference hashes to the identity hash of the reference
 * object instead; a live one keeps its referent's hash. Live entries are placed, found and compared exactly as before (their
 * hash is unchanged; strategy equality is untouched: two dead references are still equal, a dead one never equals a live
 * one), so the set's live content, every leak count and every add / contains answer for a live object stay the same. The
 * one internal difference: where dead entries sit, so the ORDER in which a summary meets the live classes of one base class
 * can differ (it also moves at every trim as shipped), and which dead entries a wrapped-iterator removal takes (any dead
 * entry is equal to any other).
 *
 * alltheleaks_crash_report_gc_skip (change, off by default) and alltheleaks_world_close_gc_background (change, off by
 * default): see their methods. On 1.21.1 both calls they change sit in CommonEvents, a class that also names client classes
 * (Minecraft, LocalPlayer), so their mixins are client mixins; a dedicated server keeps AllTheLeaks' own behaviour.
 *
 * Runtime flags: -Dbons_and_furious.alltheleaksDeadRefHashSpread, .alltheleaksCrashReportGcSkip,
 * .alltheleaksWorldCloseGcBackground (=false: AllTheLeaks' own path).
 */
public final class AtlLean {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean hashSpread = flag("alltheleaksDeadRefHashSpread");
    public static volatile boolean crashGcSkip = flag("alltheleaksCrashReportGcSkip");
    public static volatile boolean closeGcBackground = flag("alltheleaksWorldCloseGcBackground");
    /** For the harness: the last world-close thread. */
    public static volatile Thread lastCloseThread;
    private static volatile boolean crashAnnounced, closeAnnounced;
    private static final ThreadLocal<Boolean> DEFERRED = new ThreadLocal<>();

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

    // ---- alltheleaks_crash_report_gc_skip (change, off by default) ---------------------------------------------------

    /**
     * CommonEvents.generateReportForCrashReport: AllTheLeaks forces a full garbage collection (System.gc, and with its memory
     * statistics on also its Windows working-set trim) on every second crash report so that its leak list counts only
     * objects that are really alive. Trade-off: not called; the leak list in the crash report can count objects that were
     * about to be collected, and "Explicit GC Last Run" shows the older time. The rest of the section (its clearNullReferences
     * included), every count and the report counter are as shipped.
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
     * CommonEvents.onClosedServer (ServerStoppedEvent): AllTheLeaks forces a full garbage collection and then drops its dead
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

    /** CommonEvents.onClosedServer's clearNullReferences call: done by the background thread when it was started here. */
    public static void worldCloseClear(Operation<Void> original) {
        if (DEFERRED.get() == Boolean.TRUE) {
            DEFERRED.remove();
            return;
        }
        original.call();
    }
}
