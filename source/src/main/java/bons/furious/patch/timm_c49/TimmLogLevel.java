package bons.furious.patch.timm_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;

/**
 * Bons and Furious switch timm_debug_log_level (The Immersive Music Mod 1.1 for NeoForge 1.21, tested build; both sides;
 * since 1.0.49). Kind: fix (log spam). Idea: Collections Of Optimizations (timm.debugLogToDebug), idea text only.
 *
 * What TIMM does. Its debug helper Timm.debugLog(String) writes every message at INFO level, unconditionally. Its callers run
 * all the time: MusicManagerMixin's biome-switch check logs "Current biome not registered in playlist: fade out to default"
 * on every client tick while a song plays in a biome without a playlist (20 lines a second), and ServerPlayerMixin logs
 * "Structure distance was not registered for: <name>" once a second per player for every structure within two chunks
 * whose name the structure playlist lacks.
 *
 * What the switch does. MixinExtras @WrapOperation on the one Logger.info(String) call in Timm.debugLog: the same message goes
 * to the same logger at DEBUG level instead (NeoForge's logging setup still writes DEBUG lines to logs/debug.log; the console
 * and logs/latest.log no longer get them). Nothing else changes: TIMM's other log lines (its INFO start-up lines, warnings and
 * errors) do not go through debugLog.
 *
 * -Dbons_and_furious.timmDebugLogLevel=false logs at INFO again (emergency off).
 */
public final class TimmLogLevel {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.timmDebugLogLevel", "true"));
    private static volatile boolean announced;

    private TimmLogLevel() {
    }

    /** Timm.debugLog: its LOGGER.info(debugString) call. */
    public static void log(Logger logger, String message, Operation<Void> original) {
        if (!enabled || logger == null) {
            original.call(logger, message);
            return;
        }
        if (!announced) announce();
        logger.debug(message);
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: timm_debug_log_level applies (The Immersive Music Mod's debug messages are written at DEBUG level, so they no longer fill the console and latest.log)");
    }
}
