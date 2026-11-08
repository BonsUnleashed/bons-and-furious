package bons.furious.patch.flywheel_workers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;

/**
 * Bons and Furious switch flywheel_worker_parking (Flywheel 1.0.6, Jar-in-Jar in Create 6.0.10 for NeoForge 1.21.1; client).
 * Exact variant.
 *
 * What Flywheel does. ParallelTaskExecutor.execute(task) adds the task to its queue and calls
 * ThreadGroupNotifier.postNotification(), a synchronized notifyAll(); a worker with nothing to do spins for 10 us and then
 * calls awaitNotification(), a synchronized wait() without a timeout. So the render thread enters the notifier's monitor
 * once per task, and every task wakes every sleeping worker, which then all compete for that monitor on the way out of
 * wait(). The workers run at priority 3; when chunk generation keeps every core busy, a worker that holds the monitor
 * (entering or leaving wait()) can be descheduled, and the render thread's next execute() blocks behind it. A worker that
 * checked the queue just before a task was added and called wait() just after its notifyAll also sleeps on with the task
 * queued until the next execute (a lost wake-up; the render thread's own syncPoint drains such tasks itself).
 *
 * What the switch does instead (only the wake-up mechanism; the queue, the order tasks are taken in, the WaitGroup, the
 * priorities and the 10 us spin are Flywheel's own):
 * <ul>
 * <li>a worker that would wait() arms its own slot (an AtomicIntegerArray entry, one per worker), checks the queue and the
 *     running flag once more and parks with LockSupport (a 100 ms safety timeout); it disarms its slot when it wakes and
 *     clears an interrupt as stock's wait() did (InterruptedException caught);</li>
 * <li>execute() unparks ONE armed worker (a lock-free scan of at most threadCount slots, nothing to do when none is armed);
 *     the render thread never touches the monitor;</li>
 * <li>stopWorkers() unparks every armed worker right after its own notifyAll (so join() returns at once) and frees the
 *     slots after the joins.</li>
 * </ul>
 * Why no task is left sleeping: the worker arms (a volatile write) before it reads the queue, and execute() adds to the
 * queue before it reads the slots; with Java's sequentially consistent volatile/CAS accesses either the worker sees the
 * task and does not park, or execute() sees the armed slot and unparks it (an unpark before park makes park return at
 * once). Stock's lost wake-up cannot happen; the safety timeout only bounds the idle cost of a broken invariant
 * (10 wake-ups per second per idle worker).
 *
 * The one internal difference (the "Exact variant" label): workers sleep in LockSupport.park instead of the notifier's
 * monitor, and execute() wakes one sleeping worker instead of all of them. Which worker runs which task was never fixed.
 *
 * Toggling at run time is safe in both directions: with the flag off a sleeping worker waits on the monitor as in stock
 * (counted), execute() in either mode also makes the stock notifyAll while such workers exist, and unparks armed workers.
 * -Dbons_and_furious.flywheelWorkerParking=false switches it off.
 * -Dbons_and_furious.flywheelWorkerParking.shadow=true (verification runs): a worker that wakes by the safety timeout
 * counts SHADOW_CHECKS; it counts SHADOW_MISMATCHES (at most 20 WARN lines) when a task is queued, the executor runs, execute()
 * unparked nobody during this worker's whole park (WAKES did not move) and no execute() is in progress (none within the last
 * 2 ms): while this worker is armed every execute() must claim some armed worker, so such a task was never handed to anyone.
 * A task whose wake-up went to another armed worker that the OS has not scheduled yet is not one. PARKS and WAKES count parks
 * and execute() unparks.
 */
public final class FlywheelParking {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.flywheelWorkerParking", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.flywheelWorkerParking.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    public static final AtomicLong PARKS = new AtomicLong(), WAKES = new AtomicLong();
    public static final long SAFETY_NANOS = 100_000_000L;
    private static volatile boolean announced;
    /** Shadow mode only: when execute() last made its wake-up call. */
    private static volatile long lastNotifyNanos;

    private FlywheelParking() {
    }

    /** One per ParallelTaskExecutor (a field our mixin adds, created in its constructor). */
    public static final class Idle {
        private final AtomicReference<Slots> slots = new AtomicReference<>();
        private final AtomicInteger nextSlot = new AtomicInteger();
        /** Workers waiting on the notifier's monitor (flag off, or a worker without a slot). */
        final AtomicInteger monitorWaiters = new AtomicInteger();

        Slots slots(int threadCount) {
            Slots s = slots.get();
            if (s == null) {
                slots.compareAndSet(null, new Slots(Math.max(1, threadCount)));
                s = slots.get();
            }
            return s;
        }

        /** A worker's own slot index, handed out once per worker thread (stopWorkers frees them all). */
        public int assignSlot() {
            return nextSlot.getAndIncrement();
        }
    }

    static final class Slots {
        final Thread[] threads;
        /** 1 = that worker is parked (or about to park) and may be woken; 0 = running, spinning or already claimed. */
        final AtomicIntegerArray armed;
        final AtomicInteger armedCount = new AtomicInteger();

        Slots(int n) {
            threads = new Thread[n];
            armed = new AtomicIntegerArray(n);
        }
    }

    /** What the executor offers the helper (ParallelTaskExecutor implements it through our mixin). */
    public interface Host {
        Idle bons$idle();

        Deque<Runnable> bons$taskQueue();

        AtomicBoolean bons$running();

        int bons$threadCount();
    }

    /** ParallelTaskExecutor.execute's postNotification() call, after the task was queued. */
    public static void notifyWorkers(Host host, Object notifier, Operation<Void> original) {
        if (SHADOW) lastNotifyNanos = System.nanoTime();
        Idle idle = host.bons$idle();
        if (enabled) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: flywheel_worker_parking applies (Flywheel's workers sleep with park/unpark; a new task wakes one of them)");
            }
            wakeOne(idle);
            if (idle.monitorWaiters.get() > 0) original.call(notifier);   // workers on the monitor from before the flag went on
            return;
        }
        original.call(notifier);
        Slots s = idle.slots.get();
        if (s != null && s.armedCount.get() > 0) wakeAll(idle);           // workers parked by us before the flag went off
    }

    /** Unparks one armed worker; false when none was armed. */
    static boolean wakeOne(Idle idle) {
        Slots s = idle.slots.get();
        if (s == null || s.armedCount.get() <= 0) return false;
        int n = s.threads.length;
        for (int i = 0; i < n; i++) {
            if (s.armed.get(i) == 1 && s.armed.compareAndSet(i, 1, 0)) {
                s.armedCount.decrementAndGet();
                LockSupport.unpark(s.threads[i]);
                if (SHADOW) WAKES.incrementAndGet();
                return true;
            }
        }
        return false;
    }

    /** Unparks every armed worker (stopWorkers, and the flag going off). */
    public static void wakeAll(Idle idle) {
        Slots s = idle.slots.get();
        if (s == null) return;
        for (int i = 0; i < s.threads.length; i++) {
            if (s.armed.get(i) == 1 && s.armed.compareAndSet(i, 1, 0)) {
                s.armedCount.decrementAndGet();
                LockSupport.unpark(s.threads[i]);
            }
        }
    }

    /** ParallelTaskExecutor.stopWorkers' notifyAll() call (inside its synchronized block): the call, then every armed worker. */
    public static void stopNotify(Host host, Object notifier, Operation<Void> original) {
        original.call(notifier);
        wakeAll(host.bons$idle());
    }

    /** RETURN of stopWorkers: every worker has been joined, so the slots are free for the next startWorkers. */
    public static void afterStop(Host host) {
        if (host.bons$running().get()) return;   // a startWorkers raced it: keep the slots
        Idle idle = host.bons$idle();
        idle.slots.set(null);
        idle.nextSlot.set(0);
    }

    /**
     * WorkerThread.spinThenWait's awaitNotification() call (the 10 us spin found nothing). {@code slot} is the worker's own
     * index (Idle.assignSlot, kept by the worker thread).
     */
    public static void park(Host host, int slot, Object notifier, Operation<Void> original) {
        Idle idle = host.bons$idle();
        Slots s = enabled ? idle.slots(host.bons$threadCount()) : null;
        if (s == null || slot < 0 || slot >= s.threads.length) {
            idle.monitorWaiters.incrementAndGet();
            try {
                original.call(notifier);
            } finally {
                idle.monitorWaiters.decrementAndGet();
            }
            return;
        }
        Deque<Runnable> queue = host.bons$taskQueue();
        AtomicBoolean running = host.bons$running();
        s.threads[slot] = Thread.currentThread();       // published by the volatile write below
        s.armed.set(slot, 1);
        s.armedCount.incrementAndGet();
        if (!queue.isEmpty() || !running.get()) {       // a task came, or stopWorkers began, after the spin's last look
            disarm(s, slot);
            return;
        }
        long start = 0L, wakesBefore = 0L;
        if (SHADOW) {
            PARKS.incrementAndGet();
            start = System.nanoTime();
            wakesBefore = WAKES.get();
        }
        LockSupport.parkNanos(notifier, SAFETY_NANOS);
        Thread.interrupted();                           // stock: wait()'s InterruptedException is caught and dropped
        boolean queued = SHADOW && !queue.isEmpty();    // read while this worker still holds its slot
        boolean unclaimed = disarm(s, slot);            // still armed: nobody unparked this worker (timeout or spurious)
        if (SHADOW && unclaimed && System.nanoTime() - start >= SAFETY_NANOS) {
            SHADOW_CHECKS.incrementAndGet();
            if (queued && running.get()) {
                // execute() adds the task and then wakes one armed worker: an add caught between those two steps is no lost
                // wake-up, nor is a task handed to another worker that the OS has not scheduled yet (WAKES moved).
                LockSupport.parkNanos(300_000L);
                if (WAKES.get() == wakesBefore && System.nanoTime() - lastNotifyNanos > 2_000_000L) {
                    long m = SHADOW_MISMATCHES.incrementAndGet();
                    if (m <= 20) LOGGER.warn("Bons and Furious: flywheel_worker_parking shadow mismatch #{}: a worker slept through a queued task until the safety timeout", m);
                }
            }
        }
    }

    /** Disarms the slot if it is still armed; true when it was (nobody had claimed it). */
    private static boolean disarm(Slots s, int slot) {
        if (s.armed.compareAndSet(slot, 1, 0)) {
            s.armedCount.decrementAndGet();
            return true;
        }
        return false;
    }

    /** For the offline proof: armed slots right now (-1 when no slots exist). */
    public static int armedForTest(Host host) {
        Slots s = host.bons$idle().slots.get();
        return s == null ? -1 : s.armedCount.get();
    }

    /** For the offline proof: workers waiting on the monitor right now. */
    public static int monitorWaitersForTest(Host host) {
        return host.bons$idle().monitorWaiters.get();
    }
}
