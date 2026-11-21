package bons.furious.patch.distanthorizons_light_check;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Bons and Furious switch distanthorizons_light_section_lock_skip (Distant Horizons 3.3.2, LGPL-3.0; both sides; OFF by default:
 * it changes what happens when Distant Horizons itself has a threading bug).
 *
 * <p>Distant Horizons' own lighting engine (DhLightingEngine, which bakes block and sky light into every chunk it turns into
 * LOD data, on the server as well as on the client) reads and writes light values through ChunkLightStorage$LightSection.get /
 * set. Each call first takes a ReentrantLock with tryLock() and throws ConcurrentModificationException when another thread holds
 * it, then unlocks in a finally block: a debug check that two threads never touch the same section at once (added in DH core
 * commit de5e4a67, 2024-12-14; still in DH 3.3.3 and its main branch on 2026-11-16), never a wait. It costs a compare-and-swap
 * and a release on every light value: 17.8 % of the LOD builder thread while arriving on a planet, about 25 % in the Nether,
 * where lava's block light dominates (profiles of 2026-11-20).
 *
 * <p>With the switch both calls are answered without the lock: tryLock() as true and unlock() as a no-op, as a pair, so the
 * lock is never touched. In every run in which Distant Horizons has no threading bug the light values are the same; in a run in
 * which two threads did touch one section at once, Distant Horizons would no longer stop that task with the exception. That
 * is why it is off by default. Runtime flag -Dbons_and_furious.dhLightCheck=keep keeps the check even with the switch on.
 */
public final class LightCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** True: the two lock calls are skipped (the config switch decides whether the mixin exists at all). */
    public static final boolean SKIP = !"keep".equalsIgnoreCase(System.getProperty("bons_and_furious.dhLightCheck", "skip"));
    private static volatile boolean announced;

    private LightCheck() {
    }

    public static boolean announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_light_section_lock_skip applies (Distant Horizons' per-call light section lock check is skipped; "
                    + "a threading bug in Distant Horizons would no longer throw)");
        }
        return true;
    }
}
