package bons.furious.patch.distanthorizons_c2;

import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.world.DhClientWorld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix distanthorizons_join_config_resend (Distant Horizons 3.3.2, LGPL-3.0; client; since 1.0.34). No
 * Distant Horizons code is carried.
 *
 * Joining a server, Distant Horizons sends the client's session config from the DhClientWorld constructor, and only
 * afterwards SharedApi.setDhWorld creates the thread pool that handles the server's messages
 * (ThreadPoolUtil.setupThreadPools). A reply that arrives in between is dropped (ClientApi logs a bare "warn"). The
 * server's SessionConfigMessage is the only message that tells the client the server has full Distant Horizons support;
 * without it the client never asks the server for LODs, so distant terrain appears only where the player has been. The
 * reply loses that race whenever it is faster than the client's own setup: on a server that registered the player before
 * the client's Distant Horizons started, it arrives within a few milliseconds.
 *
 * Right after setupThreadPools(), when the new world is a client world whose network state has not received the server's
 * config (ClientNetworkState.isReady() is false: the reply was dropped or is still on its way), the config is sent again.
 * The server answers every config message the same way (it stores the client's constraints and replies), and that answer
 * finds the pool. When the first reply was only late, the client receives the same config twice; the generator plan is
 * unchanged, so no listener runs and nothing else changes. Singleplayer (the client-server world) is not touched, and a
 * server without Distant Horizons gets the same unanswered message once more.
 *
 * -Dbons_and_furious.distanthorizonsJoinConfigResend=false switches it off at run time.
 */
public final class JoinConfigResend {
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.distanthorizonsJoinConfigResend", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced, warned;
    /** Config messages sent again (for probes and rigs). */
    public static volatile long resends;

    private JoinConfigResend() {
    }

    /** SharedApi.setDhWorld, right after ThreadPoolUtil.setupThreadPools() (the load branch). */
    public static void afterPools(AbstractDhWorld world) {
        if (!enabled || !(world instanceof DhClientWorld client) || client.networkState == null) return;
        try {
            if (client.networkState.isReady()) return;
            client.networkState.sendConfigMessage();
            resends++;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: distanthorizons_join_config_resend: Distant Horizons' session config was sent again "
                        + "once its message threads existed (a server reply that came earlier is dropped by Distant Horizons)");
            } else {
                LOGGER.debug("Bons and Furious: distanthorizons_join_config_resend: session config sent again");
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Bons and Furious: distanthorizons_join_config_resend could not send Distant Horizons' session config again ({})", t.toString());
            }
        }
    }
}
