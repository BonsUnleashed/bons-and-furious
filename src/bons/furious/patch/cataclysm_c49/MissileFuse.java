package bons.furious.patch.cataclysm_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cataclysm_missile_fuse_unsynced (L_Ender's Cataclysm 3.16, 3.31 the same; server; since 1.0.49;
 * a trade-off, off by default). Idea: Collections Of Optimizations (cataclysm.unsyncedMissileFuse), idea text only. Our own
 * logic; no Cataclysm code (CC-BY-NC-ND-4.0, injectors only).
 *
 * The Wither homing missile (the Harbinger's and the Prowler's) keeps its fuse (80 ticks from launch) as synced entity data
 * and counts it down every tick on both sides with setFuse(getFuse() - 1); on the server each such write marks the data
 * dirty, so every player tracking the missile gets an entity-data packet every tick. Nothing but that countdown reads the
 * fuse: the tick (the server explodes the missile when it reaches 0) and the save (the "fuse" tag).
 *
 * What changes (server levels only): the tick's getFuse / setFuse and the save's getFuse use a countdown the missile keeps
 * itself (MissileFuseMixin), taken from the synced fuse the first time and again whenever the synced fuse changes from
 * outside (a load, the launch, a command). The synced fuse keeps the value the clients got when the missile appeared, so no
 * entity-data packet is sent for it; each client counts its own copy down as before. The server explodes the missile on the
 * same tick and saves the same fuse. The difference: the clients' fuse copies are no longer corrected from the server (a
 * player who starts tracking a missile mid-flight starts from the launch value); no client code reads them.
 *
 * -Dbons_and_furious.cataclysmMissileFuseUnsynced=false keeps the synced countdown even with the switch on.
 */
public final class MissileFuse {
    public static final String KEY = "cataclysm_missile_fuse_unsynced";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmMissileFuseUnsynced", "true"));
    private static volatile boolean announced;

    private MissileFuse() {
    }

    public static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Cataclysm's homing missiles count their fuse down without an entity-data update every tick)", KEY);
        }
    }
}
