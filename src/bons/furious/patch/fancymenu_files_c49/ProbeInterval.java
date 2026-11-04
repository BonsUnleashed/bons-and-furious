package bons.furious.patch.fancymenu_files_c49;

import bons.pure.config.PureConfig;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch fancymenu_internet_probe_interval (FancyMenu 3.9.14; client side; kind change, off by default).
 * Ours; none of FancyMenu's code is carried.
 *
 * What FancyMenu does. WebUtils' static initialiser builds its InternetAvailabilityMonitor with a 20-second refresh delay:
 * once WebUtils.init() runs, a background thread ("FancyMenu-WebUtils-ConnectivityCheck") sends an HTTPS request to
 * https://google.com (3 s connect and read timeouts) right away and then every 20 seconds, and publishes the answer that
 * the "internet is available" requirement and the web text placeholders read.
 *
 * What the switch does (trade-off). The monitor gets a refresh delay of N seconds instead (value line
 * fancymenu_internet_probe_interval.value, default 600, range 20 to 86400); the first check still runs at once. FancyMenu
 * then notices a lost or restored connection up to N seconds late, so layouts that depend on it switch later.
 *
 * -Dbons_and_furious.fancymenuInternetProbeInterval=false switches it off at run time (the monitor keeps 20 seconds).
 */
public final class ProbeInterval {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fancymenuInternetProbeInterval", "true"));

    private ProbeInterval() {}

    /** In place of the refresh delay WebUtils' static initialiser hands to the monitor. */
    public static Duration delay(Duration stock) {
        if (!enabled) return stock;
        int seconds = PureConfig.intValue("fancymenu_internet_probe_interval", 600, 20, 86400);
        LOGGER.info("Bons and Furious: fancymenu_internet_probe_interval applies (FancyMenu checks the internet connection every {} s instead of {} s)",
                seconds, stock.getSeconds());
        return Duration.ofSeconds(seconds);
    }
}
