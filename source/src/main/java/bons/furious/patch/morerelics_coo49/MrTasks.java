package bons.furious.patch.morerelics_coo49;

import com.blorb.morerelics.helper.DelayedTaskManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch morerelics_delayed_task_safety (More Relics 1.7.7 for Relics 0.12.8 on NeoForge 1.21.1; All Rights
 * Reserved; server side; since 1.0.49). Fix. Ours; no More Relics code is carried. Idea: Collections Of Optimizations
 * (morerelics.safeDelayedTasks), idea text only.
 *
 * DelayedTaskManager keeps one static ArrayList of (runnable, ticks) tasks. At the START of every server tick
 * (ServerTickEvent.Pre) onServerEvent walks the list with its iterator: a task at 0 ticks runs and is noted for removal,
 * the others count down; after the walk the noted tasks are removed (List.remove, by identity: DelayedTask keeps Object's
 * equals).
 * Two defects:
 *  1. a task that calls addTask while it runs changes the list under the iterator, and the next step of the walk throws
 *     ConcurrentModificationException out of the server tick (no 1.7.7 task does it yet: Twin Fangs' follow-up hit is
 *     guarded by inDualStrike; anything else that schedules from a hurt or jump handler reached from a task would);
 *  2. nothing ever empties the list: tasks pending when a single-player world closes (Twin Fangs' second hit, Gravitum
 *     Strider's clouds) hold that world's player and level and run in the next world opened in the same game; and a
 *     dropped Twin Fangs task leaves TwinFangs.inDualStrike true (set before the second hit is scheduled, cleared only by
 *     that task), which switches Twin Fangs' double strike off.
 *
 * passIterator (in place of the walk's tasks.iterator()) walks the list by index up to the size it had when the pass
 * started: the walk only reads the list and a task can only append to it (addTask), so those first entries are the
 * tasks the pass started with, in their order. With no task added during the pass that is the very same sequence, so
 * every run, count-down and removal is the original's; a task added during the pass is not visited until the next pass
 * (where it counts down from its delay like a task added between ticks) instead of breaking the walk, and nothing is
 * copied (a list that is not random-access, which DelayedTaskManager never makes, is walked over a copy instead).
 * onServerStopped (a ServerStoppedEvent listener registered once, when DelayedTaskManager's static initialiser has run)
 * empties the list and sets TwinFangs.inDualStrike back to false; a running server never sees it, and a dedicated server's
 * JVM ends right after it. Runtime flag -Dbons_and_furious.morerelicsDelayedTaskSafety=false.
 */
public final class MrTasks {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.morerelicsDelayedTaskSafety", "true"));
    /** The stop listener is registered (statistics and the harness). */
    public static volatile boolean installed;
    /** Tasks dropped at server stops (statistics; plain, racy). */
    public static long dropped;
    private static volatile boolean announced;

    private MrTasks() {
    }

    /** In place of tasks.iterator() at the start of DelayedTaskManager.onServerEvent's walk. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterator passIterator(List tasks, Operation<Iterator> original) {
        if (!enabled || tasks == null) return original.call(tasks);
        // always the same iterator class here (also for an empty list): the walk's hasNext / next stay monomorphic and
        // the JIT can keep the iterator off the heap, as it does the list's own
        return tasks instanceof RandomAccess ? new Pass(tasks) : new ArrayList(tasks).iterator();
    }

    /** The first n entries of the list by index, n = its size when the pass starts. */
    static final class Pass implements Iterator<Object> {
        private final List<?> tasks;
        private final int end;
        private int next;

        Pass(List<?> tasks) {
            this.tasks = tasks;
            this.end = tasks.size();
        }

        @Override
        public boolean hasNext() {
            return next < end;
        }

        @Override
        public Object next() {
            if (next >= end) throw new NoSuchElementException();
            return tasks.get(next++);
        }
    }

    /** Called once from DelayedTaskManager's static initialiser. */
    public static synchronized void install() {
        if (installed) return;
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, MrTasks::onServerStopped);
            installed = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: morerelics_delayed_task_safety could not register its server-stop listener ({}); More Relics' task list is not emptied when a world closes", t.toString());
        }
    }

    /** ServerStoppedEvent: forget the closed world's tasks and the Twin Fangs strike flag. */
    public static void onServerStopped(ServerStoppedEvent event) {
        if (!enabled) return;
        List<?> tasks = DelayedTaskManager.tasks;
        int n = tasks == null ? 0 : tasks.size();
        if (n > 0) tasks.clear();
        dropped += n;
        resetTwinFangs();
        if (n > 0 && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: morerelics_delayed_task_safety dropped {} More Relics task(s) still pending when the server stopped", n);
        }
    }

    private static MethodHandle inDualStrike;
    private static boolean looked;

    private static void resetTwinFangs() {
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName("com.blorb.morerelics.relics.TwinFangs", false, DelayedTaskManager.class.getClassLoader());
                inDualStrike = MethodHandles.lookup().findStaticSetter(c, "inDualStrike", boolean.class);
            } catch (Throwable t) {
                LOGGER.warn("Bons and Furious: morerelics_delayed_task_safety cannot reach TwinFangs.inDualStrike ({}); it is not reset at server stops", t.toString());
            }
        }
        if (inDualStrike != null) {
            try {
                inDualStrike.invokeExact(false);
            } catch (Throwable t) {
                LOGGER.warn("Bons and Furious: morerelics_delayed_task_safety could not reset TwinFangs.inDualStrike ({})", t.toString());
            }
        }
    }
}
