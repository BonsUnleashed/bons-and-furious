package bons.furious.patch.simplyswordsoverhaul_coo49;

import java.util.List;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch simplyswordsoverhaul_server_stop_reset (Simply Swords Overhaul 1.0.4 with Simply Swords 1.56.0;
 * MIT; server side; since 1.0.49). Fix. Ours; no Simply Swords Overhaul code is carried. Idea: Collections Of
 * Optimizations (simplyswordsoverhaul.resetSchedulerOnServerStop), idea text only.
 *
 * Simply Swords Overhaul's Scheduler keeps one static list of timed tasks (every step of the Whisperwind, Storm's Edge
 * and Emberlash dashes, Stars Edge's delayed recall) and runs it at the END of every server tick, under the list's lock.
 * Nothing ever empties it, and the per-player maps beside it (SsoMaps) are only cleaned by those tasks. When a
 * single-player world closes with tasks pending, the tasks keep that world's player and level objects reachable and run
 * their remaining steps against them once the next world opened in the same game starts ticking; a Stars Edge recall spot
 * saved in one world stays in the map and is used by the next Stars Edge use, in whatever world that is.
 *
 * install (from Scheduler's static initialiser) keeps the list and registers onServerStopped, which empties the list (under
 * its lock, as the scheduler itself takes it) and both maps. A running server never sees it; a dedicated server's JVM ends
 * right after it. Runtime flag -Dbons_and_furious.simplyswordsoverhaulServerStopReset=false.
 */
public final class SsoStop {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.simplyswordsoverhaulServerStopReset", "true"));
    /** The stop listener is registered (statistics and the harness). */
    public static volatile boolean installed;
    /** Tasks and map entries dropped at server stops (statistics; plain, racy). */
    public static long dropped, forgotten;
    private static volatile List<?> tasks;
    private static volatile boolean announced;

    private SsoStop() {
    }

    /** Called once from Scheduler's static initialiser with its task list. */
    public static synchronized void install(List<?> list) {
        tasks = list;
        if (installed) return;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, SsoStop::onServerStopped);
            installed = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: simplyswordsoverhaul_server_stop_reset could not register its server-stop listener ({}); Simply Swords Overhaul's tasks are not dropped when a world closes", t.toString());
        }
    }

    /** ServerStoppedEvent: forget the closed world's tasks, recall spots and dash hit lists. */
    public static void onServerStopped(ServerStoppedEvent event) {
        if (!enabled) return;
        List<?> list = tasks;
        int n = 0;
        if (list != null) {
            synchronized (list) {
                n = list.size();
                list.clear();
            }
        }
        int m = SsoMaps.clearAll();
        dropped += n;
        forgotten += m;
        if (n + m > 0 && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: simplyswordsoverhaul_server_stop_reset dropped {} Simply Swords Overhaul task(s) and {} saved player entr(ies) when the server stopped", n, m);
        }
    }
}
