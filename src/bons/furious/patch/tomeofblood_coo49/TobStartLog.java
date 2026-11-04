package bons.furious.patch.tomeofblood_coo49;

/**
 * Bons and Furious fix tomeofblood_server_start_log_silence (Tome of Blood: Rebirth 0.4.5, LGPL-3.0; logical server, the
 * integrated server in single player too). Ours; no Tome of Blood code is carried. Idea: Collections Of Optimizations
 * (tomeofblood.silenceServerStartLog), idea text only.
 *
 * Tome of Blood still carries the mod template's example ServerStartingEvent handler, whose whole body is
 * LOGGER.info("HELLO from server starting"): one meaningless line in every server start and world load. The switch drops
 * exactly that call (the message must be that template line; any other text is logged as before). It writes no line of its
 * own. Runtime flag: -Dbons_and_furious.tomeofbloodServerStartLogSilence=false.
 */
public final class TobStartLog {
    public static final String KEY = "tomeofblood_server_start_log_silence";
    public static final String LINE = "HELLO from server starting";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tomeofbloodServerStartLogSilence", "true"));
    /** Statistics for probes (plain field): lines dropped. */
    public static long dropped;

    private TobStartLog() {
    }

    /** True when the wrapped Logger.info call is made (the condition of TobServerStartLogMixin). */
    public static boolean keep(String message) {
        if (!enabled || !LINE.equals(message)) return true;
        dropped++;
        return false;
    }
}
