package bons.furious.patch.crittersandcompanions_coo49;

import bons.pure.config.PureConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch crittersandcompanions_food_scan_interval (Critters and Companions 2.3.5 otters; 2.7.1 otters and
 * leaf insects; server).
 *
 * An otter (and in 2.7.1 a leaf insect) with nothing in its mouth asks the level for every dropped item within 8 blocks of
 * it each time its goal selector checks the food goal (every other tick), to find something to pick up. The switch
 * lets an idle critter run that search only when INTERVAL or more ticks have passed since its last one; in between the
 * check answers as an empty search does (no food seen). A critter that already found food keeps searching on every check
 * while it goes for the item, so a running goal never stops early.
 * Trade-off (off by default): a dropped item can be noticed up to INTERVAL - 1 ticks later than before. INTERVAL is the
 * switch's value (crittersandcompanions_food_scan_interval.value, default 4 = COO's default, 1 = every check as before).
 * Runtime flag -Dbons_and_furious.crittersandcompanionsFoodScanInterval=false. No Critters and Companions code is carried.
 */
public final class FoodScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.crittersandcompanionsFoodScanInterval", "true"));
    /** Ticks between the searches of an idle critter (read on first use, the switch-on path only). */
    public static final int INTERVAL = PureConfig.intValue("crittersandcompanions_food_scan_interval", 4, 1, 200);
    private static volatile boolean announced;

    private FoodScan() {
    }

    /** True when an idle critter's search at game time `now` is skipped (its last search was less than INTERVAL ago). */
    public static boolean skip(long now, long lastSearch) {
        if (!enabled || lastSearch == Long.MIN_VALUE || now - lastSearch >= INTERVAL || now < lastSearch) return false;
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: crittersandcompanions_food_scan_interval: idle otters and leaf insects look for dropped food every {} ticks", INTERVAL);
    }
}
