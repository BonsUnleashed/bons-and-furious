package bons.furious.patch.lethality_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lethality_sos_name_server_fallback (Lethality 0.4.1, MIT; both sides, acts on a dedicated server;
 * since 1.0.49; kind fix). Idea: Collections Of Optimizations (lethality.fixServerSideSosName), idea text only.
 *
 * The Backup SOS and Ultra Backup SOS charms build their name and tooltip with pasted copies of Lethality's colour gradient
 * helpers (addColorGradientText / addColorGradientTextWithFont with speed and spread). Those copies read the animation
 * tick as (Integer) DistExecutor.unsafeCallWhenOn(Dist.CLIENT, ...): on a dedicated server that call answers null and the
 * unboxing throws NullPointerException whenever the server builds the charm's name (an anvil rename, /give feedback, a
 * chat or death message naming the item).
 *
 * The fix: a null answer is replaced by (int) (System.currentTimeMillis() / 50), the fallback of Lethality's own
 * ModUtils.getTickCount, whose helpers these methods copy. On a client (and in single player) the call never answers null
 * (it runs the client supplier), so nothing changes there.
 *
 * -Dbons_and_furious.lethalitySosNameServerFallback=false runs the methods as shipped.
 */
public final class LethalitySos {
    public static final String KEY = "lethality_sos_name_server_fallback";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lethalitySosNameServerFallback", "true"));
    private static volatile boolean announced;

    private LethalitySos() {
    }

    /** The answer of the wrapped DistExecutor.unsafeCallWhenOn(CLIENT, ...) tick read: null becomes the tick fallback. */
    public static Object tick(Object answer) {
        if (answer != null || !enabled) return answer;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (the Backup SOS charms' names are built with Lethality's tick fallback on this server)", KEY);
        }
        return Integer.valueOf((int) (System.currentTimeMillis() / 50L));
    }
}
