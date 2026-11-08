package bons.furious.patch.corpse_coo49;

import bons.pure.config.PureConfig;
import de.maxhenkel.corpse.Main;
import de.maxhenkel.corpse.corelib.death.DeathManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches corpse_death_sweep_single_worker (fix) and corpse_death_sweep_interval (change, off by
 * default), since 1.0.49; Corpse 1.21.1-1.1.22, server side. Helper of CorpseSweepWorkerMixin and CorpseSweepIntervalMixin;
 * no Corpse code is carried.
 *
 * What Corpse does. Every player death ends in DeathEvents.deleteOldDeaths(level): with maxDeathAge >= 0 days it starts a
 * NEW non-daemon thread that runs DeathManager.removeDeathsOlderThan(level, age): list every player folder of the server's
 * deaths folder (MinecraftServer.getWorldPath(deaths), one per server), read every file older than the age (NBT), and
 * delete the deaths whose own time stamp is older too, one INFO line per deleted file. Two deaths close together run two
 * of these sweeps at the same time over the same files: one deletes a file the other is reading (DeathManager.getDeath
 * prints the exception's stack trace) or both try to delete it.
 *
 * corpse_death_sweep_single_worker (fix). The sweep runs on one shared worker thread instead (a ThreadPoolExecutor with at
 * most one thread, no core thread and no keep-alive: the thread is made by new Thread(task), named "Thread-N" as Corpse's
 * own threads are, non-daemon like them, and it ends as soon as no sweep is waiting, so the JVM exits as before). Sweeps
 * therefore never overlap. A request is dropped only while a sweep of the same server with the same age is queued and has
 * not started yet: removeDeathsOlderThan takes "now" when it starts, so that queued sweep, which starts after the dropped
 * request was made, deletes every file the dropped one would have deleted (files only become older). The same files are
 * deleted, by the same code, with the same INFO lines; what goes is the second concurrent walk, its race, its stack traces
 * and one thread per death. An exception in a sweep ends that worker thread with the same uncaught-exception output as
 * Corpse's thread (execute, not submit). -Dbons_and_furious.corpseDeathSweepSingleWorker=false starts threads as before.
 *
 * corpse_death_sweep_interval (change, off by default; value: seconds, default 300, range 1-86400). A sweep request made
 * within that many seconds of the last accepted request of the same server is dropped. Trade-off: death files older than
 * maxDeathAge can stay on disk longer, until the first death after the interval (with no further deaths, until the next
 * one). Nothing else changes. -Dbons_and_furious.corpseDeathSweepInterval=false is the emergency off.
 */
public final class CorpseSweeps {
    /** corpse_death_sweep_single_worker: -Dbons_and_furious.corpseDeathSweepSingleWorker=false starts threads as before. */
    public static volatile boolean workerEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.corpseDeathSweepSingleWorker", "true"));
    /** corpse_death_sweep_interval: -Dbons_and_furious.corpseDeathSweepInterval=false accepts every request. */
    public static volatile boolean intervalEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.corpseDeathSweepInterval", "true"));
    static final String INTERVAL_KEY = "corpse_death_sweep_interval";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    /** At most one thread, none kept: an idle worker ends at once (keep-alive 0), a new request makes a new one. */
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(0, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            (java.util.concurrent.ThreadFactory) task -> new Thread(task));
    /** Keys of the sweeps that are queued and have not started (guarded by itself). */
    private static final Set<Key> QUEUED = new HashSet<>();
    /** Statistics for the offline proof and probes: sweeps handed to the worker, requests dropped by each switch. */
    public static long queued, droppedQueued, droppedInterval, acceptedInterval;
    private static volatile boolean workerAnnounced, intervalAnnounced;

    /** A sweep's identity: the server (whose deaths folder it walks; identity) and the age in milliseconds. */
    private record Key(Object server, long age) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.server == this.server && k.age == this.age;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.server) * 31 + Long.hashCode(this.age);
        }
    }

    private CorpseSweeps() {
    }

    /** DeathEvents.deleteOldDeaths, at its new Thread: the sweep goes to the worker (or is covered by a queued one). */
    public static void submit(ServerLevel level, long ageMillis) {
        if (!workerAnnounced) {
            workerAnnounced = true;
            LOGGER.info("Bons and Furious: corpse_death_sweep_single_worker applies (old death files are swept by one background thread, never two at once)");
        }
        Key key = new Key(level.getServer(), ageMillis);
        synchronized (QUEUED) {
            if (!QUEUED.add(key)) {
                droppedQueued++;
                return;
            }
            queued++;
        }
        WORKER.execute(() -> {
            synchronized (QUEUED) {
                QUEUED.remove(key);
            }
            DeathManager.removeDeathsOlderThan(level, ageMillis);
        });
    }

    /** True while a sweep is running or waiting (offline proof). */
    public static boolean busy() {
        return WORKER.getActiveCount() > 0 || !WORKER.getQueue().isEmpty();
    }

    /** The interval in milliseconds, read once, only while the switch is on. */
    private static final class Interval {
        static final long MILLIS = PureConfig.intValue(INTERVAL_KEY, 300, 1, 86400) * 1000L;
    }

    /** Last accepted request per server (nanoTime); weak keys, so a closed server's entry goes with it. */
    private static final Map<Object, Long> LAST = new WeakHashMap<>();

    /**
     * DeathEvents.deleteOldDeaths, at its start: true = drop this request (the last accepted one of this server is younger
     * than the interval). A request that would not sweep (maxDeathAge < 0, read as Corpse reads it) is never counted.
     */
    public static boolean dropByInterval(ServerLevel level) {
        int days = Main.SERVER_CONFIG.maxDeathAge.get();
        if (days < 0) return false;
        long millis = Interval.MILLIS;
        if (!intervalAnnounced) {
            intervalAnnounced = true;
            LOGGER.info("Bons and Furious: corpse_death_sweep_interval applies (old death files are swept at most once every {} seconds)", millis / 1000L);
        }
        long now = System.nanoTime();
        Object server = level.getServer();
        synchronized (LAST) {
            Long last = LAST.get(server);
            if (last != null && now - last < millis * 1_000_000L) {
                droppedInterval++;
                return true;
            }
            LAST.put(server, now);
            acceptedInterval++;
        }
        return false;
    }
}
