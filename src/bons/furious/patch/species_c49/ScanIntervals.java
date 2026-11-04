package bons.furious.patch.species_c49;

import bons.pure.config.PureConfig;
import java.util.List;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches species_limpet_scare_scan_interval and species_wicked_haunt_scan_interval (Species 3.5, since
 * 1.0.49; trade-offs, off by default). Ideas: Collections Of Optimizations (species.limpetScareScanInterval,
 * species.wickedHauntScanInterval), idea text only. Our own logic; no Species code (all rights reserved, injectors only).
 *
 * Limpet.aiStep (server) asks the level for every player in its box inflated by 4 every tick, only to set its scared
 * timer back to 100 for each one it finds; Wicked.tick (both sides), while it has a haunted mob's id but not the mob,
 * asks the level for every mob in its box inflated by 20 every tick to find that mob again.
 *
 * What changes: each of those two queries runs only on every N-th tick of the mob (its tick count plus its id, so the
 * mobs of one area spread over the ticks); on the other ticks the query answers an empty list, so the code that follows
 * finds nothing that tick. N is the switch's value (limpet default 10, wicked default 20). A limpet notices a player up to
 * N-1 ticks later and, once the player is gone, calms down up to N-1 ticks sooner; a wicked finds its haunted mob again up
 * to N-1 ticks later. With the runtime flags -Dbons_and_furious.speciesLimpetScareScanInterval=false /
 * -Dbons_and_furious.speciesWickedHauntScanInterval=false every query runs.
 */
public final class ScanIntervals {
    public static final String LIMPET_KEY = "species_limpet_scare_scan_interval", WICKED_KEY = "species_wicked_haunt_scan_interval";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean limpetEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.speciesLimpetScareScanInterval", "true"));
    public static volatile boolean wickedEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.speciesWickedHauntScanInterval", "true"));
    private static volatile boolean limpetAnnounced, wickedAnnounced;

    private ScanIntervals() {
    }

    /** The limpet's value, read on first use (this class loads from the limpet's mixin, which applies only when the switch is on). */
    static final class Limpet {
        static final int EVERY = PureConfig.intValue("species_limpet_scare_scan_interval", 10, 1, 200);
    }

    /** The wicked's value, read on first use (see Limpet). */
    static final class Wicked {
        static final int EVERY = PureConfig.intValue("species_wicked_haunt_scan_interval", 20, 1, 200);
    }

    /** True when the limpet's player query runs this tick. */
    public static boolean limpetScans(Entity limpet) {
        if (!limpetEnabled) return true;
        int every = Limpet.EVERY;
        if (every <= 1 || Math.floorMod(limpet.f_19797_ + limpet.m_19879_(), every) == 0) return true;
        if (!limpetAnnounced) {
            limpetAnnounced = true;
            LOGGER.info("Bons and Furious: {} applies (limpets look for players every {} ticks)", LIMPET_KEY, every);
        }
        return false;
    }

    /** True when the wicked's haunted-mob query runs this tick. */
    public static boolean wickedScans(Entity wicked) {
        if (!wickedEnabled) return true;
        int every = Wicked.EVERY;
        if (every <= 1 || Math.floorMod(wicked.f_19797_ + wicked.m_19879_(), every) == 0) return true;
        if (!wickedAnnounced) {
            wickedAnnounced = true;
            LOGGER.info("Bons and Furious: {} applies (a wicked looks for its haunted mob every {} ticks until it finds it)", WICKED_KEY, every);
        }
        return false;
    }

    /** What a skipped query answers. */
    @SuppressWarnings("unchecked")
    public static <T> List<T> none() {
        return (List<T>) List.of();
    }
}
