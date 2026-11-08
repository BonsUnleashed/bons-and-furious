package bons.furious.patch.boss_music_c49;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious 1.0.49 switch mowziesmobs_boss_music_stop_once (group boss_music_c49; server side; Minecraft 1.21.1 +
 * NeoForge; 1.21.1 tested build: Mowzie's Mobs 1.21.1-1.8.2). Helper of MowzieBossMusicStopOnceMixin; no code of Mowzie's
 * Mobs or of Minecraft is carried. A trade-off, off by default.
 *
 * Mowzie's Mobs MowzieEntity.tick: every server tick a boss with boss music broadcasts entity event 67 (play) while it can
 * play its music (not silent, its target is a player), else 68 (stop). With the switch on, a stop goes out only when this
 * boss's previous message since it was loaded was a play, or when it is the first message since then (one mark per boss
 * object; nothing is saved); every play goes out every tick as before (a play is not idempotent: it re-checks the hearing
 * range and restarts the sound). On the client MowzieEntity.handleEntityEvent hands 68 to BossMusicPlayer.stopBossMusic,
 * which acts only while the current music is bound to that boss object, and only a play binds it, so the repeated stops
 * change nothing there. The one difference a player can notice: a client whose own boss-music option is off at the moment
 * of the single stop and turned on again while the boss is idle keeps hearing the music until the boss plays again, dies or
 * is unloaded (with the repeats, the next stop after the option came back ends it).
 * Runtime flag (-D...=false turns it off): bons_and_furious.mowziesmobsBossMusicStopOnce.
 * SHADOW MODE: -Dbons_and_furious.bossMusicStops.shadow=true sends every stop as before and counts SHADOW_CHECKS (the stops
 * the switch would have left out); SHADOW_MISMATCHES stays 0 (there is nothing to compare on the server: the client side of
 * the argument is the in-game check).
 */
public final class MowzieBossMusicStops {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsBossMusicStopOnce", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.bossMusicStops.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields; the server thread writes them): stops sent, stops left out. */
    public static long stopsSent, stopsLeftOut;
    private static volatile boolean announced;

    private MowzieBossMusicStops() {
    }

    /**
     * A stop is about to go out; {@code stopSent} = this boss's previous message since load was a stop. True = send it
     * (the caller then remembers that a stop went out).
     */
    public static boolean sendStop(boolean stopSent) {
        if (!enabled || !stopSent) {
            stopsSent++;
            return true;
        }
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            stopsSent++;
            return true;
        }
        if (!announced) announce();
        stopsLeftOut++;
        return false;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: mowziesmobs_boss_music_stop_once applies (an idle Mowzie's Mobs boss sends its boss-music stop once)");
    }
}
