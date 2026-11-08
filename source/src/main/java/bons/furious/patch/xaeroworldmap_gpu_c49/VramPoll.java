package bons.furious.patch.xaeroworldmap_gpu_c49;

import bons.pure.config.PureConfig;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch xaeroworldmap_vram_poll_interval (Xaero's World Map 1.47.0 for NeoForge 1.21.1, tested build;
 * client; since 1.0.49). Kind: change (trade-off, off by default). Idea: Collections Of Optimizations
 * (xaeroworldmap.vramPollInterval), idea text only. Xaero's World Map is All Rights Reserved: none of its code is carried.
 *
 * What World Map does. MapLimiter.updateAvailableVRAM runs at the end of every frame's MapProcessor.onRenderProcess and,
 * on NVIDIA (GL_NVX_gpu_memory_info) and AMD (GL_ATI_meminfo) drivers, reads the free video memory with glGetIntegerv: a
 * query the driver can only answer after the commands queued before it are processed. The value is read by MapLimiter's
 * region unloading on World Map's own thread (only "below 200 MB" matters there) and shown in the map's debug line.
 *
 * What the switch changes (WmVramPollMixin, a cancellable @Inject at the method's head). Once the driver type is known and
 * it is one of those two, the query runs at most once per value (milliseconds); the frames in between keep the last value.
 * The first query and the driver detection run as shipped.
 * Trade-off: region unloading under low video memory can start up to the value later, and the debug line updates every
 * value milliseconds.
 *
 * -Dbons_and_furious.xaeroworldmapVramPollInterval=false queries every frame again (emergency off).
 */
public final class VramPoll {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaeroworldmapVramPollInterval", "true"));
    public static long skipped;
    private static volatile boolean announced;

    private VramPoll() {
    }

    private static final class Value {   // read on the first switch-on use only
        static final long NS = 1_000_000L * PureConfig.intValue("xaeroworldmap_vram_poll_interval", 500, 1, 60000);
    }

    /** True when this frame's query is left out (the last one ran less than the value ago). */
    public static boolean skip(long lastQuery, boolean queried, long now) {
        if (!queried || now - lastQuery >= Value.NS) return false;
        skipped++;
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: xaeroworldmap_vram_poll_interval applies (World Map asks the driver for free video memory at most every "
                + Value.NS / 1_000_000L + " ms)");
    }
}
