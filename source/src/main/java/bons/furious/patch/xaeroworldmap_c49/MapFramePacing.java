package bons.furious.patch.xaeroworldmap_c49;

import bons.pure.config.PureConfig;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import xaero.map.gui.GuiMap;

/**
 * Bons and Furious switches xaeroworldmap_upload_headroom and xaeroworldmap_upload_pass_interval (Xaero's World Map 1.47.0
 * for NeoForge 1.21.1, tested build; client; since 1.0.49). Kind: change (trade-offs, off by default). Xaero's World Map is
 * All Rights Reserved: none of its code is carried.
 *
 * xaeroworldmap_upload_headroom (Idea: Collections Of Optimizations (xaeroworldmap.uploadHeadroomMicros), idea text only).
 * MapProcessor.onRenderProcess gives map texture uploads uploadStart + max(3 ms, frame time - time passed) / 4 per frame, so
 * at high frame rates the 3 ms floor applies and uploads may take 750 us of every frame. The switch replaces that 3 ms
 * constant by the value (microseconds) while the world map screen is not open.
 * Trade-off: at high frame rates the map spends less of each frame uploading, so new tiles appear more slowly.
 *
 * xaeroworldmap_upload_pass_interval (Idea: Collections Of Optimizations (xaeroworldmap.renderProcessInterval), idea text
 * only). onRenderProcess runs its upload pass (buffer flush, GL error drain and state setup, a walk of every region in
 * processing, the budgeted uploads, the uploader's queue) on every frame. The switch reads pauseUploading as non-zero (so
 * the pass is left out, as when World Map pauses uploading itself) when the world map screen is not open and the last pass
 * started less than the value (milliseconds) ago; the rest of onRenderProcess (the map writer, the VRAM check, the frame
 * time bookkeeping) runs every frame as shipped.
 * Trade-off: at frame rates above 1000 / value per second, new map tiles appear up to the value later.
 *
 * -Dbons_and_furious.xaeroworldmapUploadHeadroom=false / xaeroworldmapUploadPassInterval=false: emergency off (the config
 * keys decide whether the mixins are applied at all). Values read only on the switch-on path.
 */
public final class MapFramePacing {
    public static volatile boolean headroomEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaeroworldmapUploadHeadroom", "true"));
    public static volatile boolean intervalEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaeroworldmapUploadPassInterval", "true"));
    private static long lastPassStart = Long.MIN_VALUE;
    private static volatile boolean headroomAnnounced, intervalAnnounced;
    /** Counter for the harness and rigs: passes left out. */
    public static long skippedPasses;

    private MapFramePacing() {
    }

    private static final class Headroom {   // read on the first switch-on use only
        static final long FLOOR_NS = 1000L * PureConfig.intValue("xaeroworldmap_upload_headroom", 1000, 100, 3000);
    }

    private static final class Interval {
        static final long NS = 1_000_000L * PureConfig.intValue("xaeroworldmap_upload_pass_interval", 4, 1, 1000);
    }

    /** onRenderProcess: its 3000000L constant (the upload time floor). */
    public static long uploadFloor(long original) {
        if (!headroomEnabled || mapScreenOpen()) return original;
        if (!headroomAnnounced) announce(true);
        return Headroom.FLOOR_NS;
    }

    /** onRenderProcess: its read of pauseUploading in the upload pass test; non-zero leaves the pass out. */
    public static int passGate(int pauseUploading) {
        if (pauseUploading != 0 || !intervalEnabled) return pauseUploading;
        long now = System.nanoTime();
        if (!mapScreenOpen() && lastPassStart != Long.MIN_VALUE && now - lastPassStart < Interval.NS) {
            if (!intervalAnnounced) announce(false);
            skippedPasses++;
            return 1;
        }
        lastPassStart = now;
        return pauseUploading;
    }

    static boolean mapScreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen instanceof GuiMap;
    }

    private static synchronized void announce(boolean headroom) {
        String key;
        if (headroom) {
            if (headroomAnnounced) return;
            headroomAnnounced = true;
            key = "xaeroworldmap_upload_headroom applies (World Map uploads at least " + Headroom.FLOOR_NS / 1000 + " us / 4 of a frame outside the map screen, instead of 3 ms / 4)";
        } else {
            if (intervalAnnounced) return;
            intervalAnnounced = true;
            key = "xaeroworldmap_upload_pass_interval applies (World Map's upload pass runs at most every " + Interval.NS / 1_000_000 + " ms outside the map screen)";
        }
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: " + key);
    }
}
