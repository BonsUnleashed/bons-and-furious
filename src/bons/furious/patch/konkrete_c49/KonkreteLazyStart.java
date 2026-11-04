package bons.furious.patch.konkrete_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch konkrete_volume_thread_lazy_start (Konkrete 1.8.0, Apache-2.0; client side). Ours; no Konkrete
 * code is carried.
 *
 * What Konkrete does. SoundHandler.init(), called once from Konkrete's client setup, constructs and starts a thread that
 * loops for the whole session: read the master volume from the game options, and when it changed since the last pass
 * call updateVolume() (set every registered clip's MASTER_GAIN from it), then sleep 100 ms. Only registerSound() puts a
 * clip into Konkrete's private sound map; FancyMenu, the mod that requires Konkrete in most packs, registers none, so the
 * thread wakes ten times a second to look at an empty map.
 *
 * What the switch does. The thread is still constructed in init() at the same moment (same name, daemon flag, priority,
 * thread group, context class loader and inherited thread-locals: all fixed at construction), but its start() call is
 * held here; the held thread is started once, right after the first registerSound() that leaves a clip in the map has
 * set that clip's volume itself (its setVolume call is the last statement of a successful registration).
 *
 * Exact variant. Before the first registration the map is empty, so the stock thread's passes change nothing: every
 * updateVolume() iterates no clip. registerSound() sets the new clip's gain itself, as before. The held thread's first
 * pass compares the current master volume with its initial 0.0, exactly as the stock thread's first pass did, so when the
 * master volume is not 0 it calls updateVolume() once more, which sets every clip's gain to the value registerSound()
 * just set (an identical, inaudible write); from then on it is the stock thread. The one difference: no volume thread
 * exists until a mod registers its first sound through Konkrete.
 *
 * -Dbons_and_furious.konkreteVolumeThreadLazyStart=false switches it off at run time (init() then starts the thread at
 * once, as stock).
 */
public final class KonkreteLazyStart {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.konkreteVolumeThreadLazyStart", "true"));
    /** The constructed, not yet started volume thread (guarded by the class lock). */
    private static Thread held;
    /** True while a thread is held: the only field a successful registration reads once the thread runs. */
    private static volatile boolean pending;
    private static volatile boolean announced;

    private KonkreteLazyStart() {}

    /** In place of SoundHandler.init()'s Thread.start(): start at once with the switch off, else hold the thread. */
    public static void holdOrStart(Thread thread) {
        if (!enabled) {
            thread.start();
            return;
        }
        synchronized (KonkreteLazyStart.class) {
            held = thread;
            pending = true;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: konkrete_volume_thread_lazy_start applies (Konkrete's volume thread starts with the first sound registered through it)");
        }
    }

    /** After a successful registerSound() has set its new clip's volume: start the held thread, once. */
    public static void clipRegistered() {
        if (!pending) return;
        Thread thread;
        synchronized (KonkreteLazyStart.class) {
            thread = held;
            held = null;
            pending = false;
        }
        if (thread != null) thread.start();
    }
}
