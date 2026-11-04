package bons.furious.patch.simplyswords_reread_c49;

import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch simplyswords_config_reread_interval (Simply Swords 1.56.0-1.20.1, both sides), a trade-off that is
 * off by default. Simply Swords has the custom Timefall Development License: no Simply Swords code is carried, only an
 * early return in front of its method. Ours, idea from Collections Of Optimizations (simplyswords.configRereadInterval), idea
 * text only.
 *
 * What Simply Swords does: every config read (Config.getBoolean / getFloat / getDouble / getInt, on hits, per tick for
 * Magiblade holders, per runic sword in an inventory) first calls Config.safeValueFetch(type, parent), which reads the
 * parent's json5 file from disk, parses it and puts its entries of that type into the type's own static map (four maps:
 * one per type); getX then reads that map. simplyswords_config_unchanged_reuse already skips the parse when the bytes are
 * unchanged, but the file is still read on every call (about 120 us per value read).
 *
 * What the switch does (only when bons_and_furious.properties turns it on): when the last safeValueFetch of the same type
 * was for the same parent and finished at most N ms ago (N = the switch's value line, default 1000), this call returns at
 * once: no other call has written that type's map since, so it still holds exactly what reading the same file again would
 * put there. Anything else (another parent wrote the map in between, the window passed, the value is 0) runs Simply Swords'
 * method as shipped. What the player can notice: an edit made to a Simply Swords json5 file while the game runs takes
 * effect up to N ms later (by default a second); a parse error in such a file is printed at most once per N ms instead of
 * on every read. Values are otherwise the same.
 *
 * Flag: -Dbons_and_furious.simplyswordsConfigRereadInterval=false reads the files on every call again at runtime.
 */
public final class RereadWindow {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.simplyswordsConfigRereadInterval", "true"));
    private static final String KEY = "simplyswords_config_reread_interval";
    /** Milliseconds a type's map may be reused without reading its file again (0 = never). */
    static final int WINDOW_MS = PureConfig.intValue(KEY, 1000, 0, 60000);
    private static final long WINDOW_NS = WINDOW_MS * 1_000_000L;
    private static final Object LOCK = new Object();
    // guarded by LOCK: per type, the parent of the last completed fetch and when it finished
    private static final Map<String, String> LAST_PARENT = new HashMap<>();
    private static final Map<String, Long> LAST_DONE = new HashMap<>();
    /** Fetches answered without reading (harness, probes). */
    public static long skipped;
    private static boolean announced;

    private RereadWindow() {
    }

    /** The @WrapMethod handler for Config.safeValueFetch(type, parent). */
    public static void fetch(String type, String parent, Operation<Void> original) {
        if (!enabled || WINDOW_NS <= 0) {
            original.call(type, parent);
            return;
        }
        synchronized (LOCK) {
            long now = System.nanoTime();
            Long done = LAST_DONE.get(type);
            if (done != null && parent != null && parent.equals(LAST_PARENT.get(type)) && now - done <= WINDOW_NS) {
                skipped++;
                if (!announced) {
                    announced = true;
                    LOGGER.info("Bons and Furious: simplyswords_config_reread_interval applies (Simply Swords config files are read again at most every {} ms)", WINDOW_MS);
                }
                return;
            }
            LAST_PARENT.remove(type);           // the map is being written by this call: nothing may be reused until it completes
            LAST_DONE.remove(type);
            original.call(type, parent);
            LAST_PARENT.put(type, parent);
            LAST_DONE.put(type, System.nanoTime());
        }
    }

    /** Harness: forget every window. */
    public static void reset() {
        synchronized (LOCK) {
            LAST_PARENT.clear();
            LAST_DONE.clear();
            skipped = 0;
        }
    }
}
