package bons.furious.patch.legendarymonsters_zoom_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch legendary_monsters_dead_zoom_skip (Legendary Monsters 2.2.2, also checked on 2.2.4; client). The
 * mod is All Rights Reserved: no Legendary Monsters code is carried, only one call of its listener answered without being
 * made. Ours, idea from Collections Of Optimizations (legendarymonsters.skipDeadZoomWork), idea text only.
 *
 * What Legendary Monsters does: ClientEvent.setDynamicZoom (ViewportEvent.ComputeCameraAngles, every frame) looks, while the
 * camera is detached and allowCameraZoom is on, for every DynamicCameraZoomEntity within 20 blocks of the camera entity,
 * and for each one close enough it works out a zoom and calls Camera.getMaxZoom(4 + zoom) through its CameraInvoker. That
 * call casts eight view-clip raycasts through the level; its result is stored in a local the method never reads again.
 *
 * What the switch does: that one call answers 0 without running. Why identical: the value is never used (the listener's
 * bytecode stores it and never loads it), and Camera.getMaxZoom only reads (vanilla's raycasts, and in this pack Monster
 * Expansion's mount-zoom injection, which also only reads and returns a value): no field of the camera, the level or
 * anything else is written. The entity search, the distance test and the zoom lerp still run, so the debug profiler and
 * the presence gate of legendary_monsters_shake_scan_presence_gate see the same calls as before.
 *
 * Flag: -Dbons_and_furious.legendaryMonstersDeadZoomSkip=false makes the call again at runtime.
 */
public final class DeadZoom {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.legendaryMonstersDeadZoomSkip", "true"));
    private static volatile boolean announced;

    private DeadZoom() {
    }

    /** True when the discarded Camera.getMaxZoom call of setDynamicZoom may be left out. */
    public static boolean skip() {
        if (!enabled) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: legendary_monsters_dead_zoom_skip applies (Legendary Monsters' dynamic zoom no longer casts raycasts whose result it throws away)");
        }
        return true;
    }
}
