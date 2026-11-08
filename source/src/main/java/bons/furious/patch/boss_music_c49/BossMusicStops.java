package bons.furious.patch.boss_music_c49;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Bons and Furious 1.0.49 group boss_music_c49 (server side; Minecraft 1.21.1 + NeoForge). Helper of the group's mixins; no
 * code of the target mods or of Minecraft is carried. Both switches are trade-offs, off by default.
 *
 *   cataclysm_boss_music_stop_once     L_Ender's Cataclysm (1.21.1 tested build: 3.33) Animation_Monsters.tick: every server
 *                                      tick a boss with boss music sends MessageMusic(play) to its trackers while it can
 *                                      play its music, else MessageMusic(stop) (PacketDistributor.sendToPlayersTracking
 *                                      EntityAndSelf).
 *   mowziesmobs_boss_music_stop_once   Mowzie's Mobs MowzieEntity.tick: every server tick a boss with boss music broadcasts
 *                                      entity event 67 (play) while it can play its music, else 68 (stop).
 * With the switch on, a stop goes out only when this boss's previous message since it was loaded was a play, or when it is
 * the first message since then (one per boss object; nothing is saved); every play goes out every tick as before (a play is
 * not idempotent: it re-checks the hearing range and restarts the sound). On the client both mods' stop handlers act only
 * while the playing sound is bound to that boss object, and only a play binds it, so the repeated stops change nothing
 * there. The one difference a player can notice: a client whose own boss-music option is off at the moment of the single
 * stop and turned on again while the boss is idle keeps hearing the music until the boss plays again, dies or is unloaded
 * (with the repeats, the next stop after the option came back ends it).
 * Runtime flags (-D...=false turns one off): bons_and_furious.cataclysmBossMusicStopOnce, .mowziesmobsBossMusicStopOnce.
 * SHADOW MODE: -Dbons_and_furious.bossMusicStops.shadow=true sends every stop as before and counts SHADOW_CHECKS (the stops
 * the switch would have left out); SHADOW_MISMATCHES stays 0 (there is nothing to compare on the server: the client side of
 * the argument is the in-game check).
 */
public final class BossMusicStops {
    public static volatile boolean cataclysm = flag("cataclysmBossMusicStopOnce");
    public static volatile boolean mowziesmobs = flag("mowziesmobsBossMusicStopOnce");
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.bossMusicStops.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields; the server thread writes them): stops sent, stops left out. */
    public static long stopsSent, stopsLeftOut;

    private BossMusicStops() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /**
     * A stop is about to go out; {@code stopSent} = this boss's previous message since load was a stop. True = send it
     * (the caller then remembers that a stop went out).
     */
    public static boolean sendStop(boolean on, boolean stopSent) {
        if (!on || !stopSent) {
            stopsSent++;
            return true;
        }
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            stopsSent++;
            return true;
        }
        stopsLeftOut++;
        return false;
    }
}
