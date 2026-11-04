package bons.furious.patch.hexerei_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group hexerei_coo49 (Hexerei 1.20.1-0.4.2.3; client). Helper of the group's mixins on
 * Hexerei's LightManager (its bundled copy of LambDynamicLights); Hexerei's code is MIT, ours is our own.
 *
 * <p>hexerei_empty_light_scan_fast_path (exact): LightManager.getDynamicLightLevel / getDynamicLightLevelWorld take the
 * light-source set's read lock, walk the set (an iterator) and clamp the best level; they run for every light query of
 * chunk meshing (Hexerei's TAIL handler on LevelRenderer.getLightColor, on the section-builder threads) and per rendered
 * entity per frame (its RETURN handler on EntityRenderer.getBlockLightLevel), whenever Hexerei's dynamic light toggle is
 * on. With no light source in the set the answer is Mth.clamp(0.0, 0, 15) = 0.0. This class keeps a copy of the set's
 * size, written INSIDE the set's write lock after every change (the three places that change it: addLightSource,
 * removeLightSource, clearLightSources; the set is private to LightManager and starts empty), and the two scans answer 0.0
 * at once while that copy is 0, without the lock. Why exact: a scan that reads 0 is ordered before any add that has not
 * yet written its count (the add's own lock is still held), so its 0.0 is a value the stock scan could have returned at
 * that instant; any other count takes the stock path. Hexerei's handlers are untouched (the TAIL handler still cancels).
 *
 * <p>hexerei_dynamic_lights_off (change, off by default): LightManager.shouldUpdateDynamicLight answers false, exactly
 * what Hexerei's own toggle (config dynamic_light_toggle, off by default in Hexerei) does when it is off: no Hexerei light
 * source is ever registered (addLightSource asks it first), Hexerei's getLightColor and entity light handlers return the
 * vanilla value at once, entities' Hexerei luminance stays 0. In force from the first call, so there is nothing to clear.
 * Hexerei's toggle command cannot turn its lights back on while the switch is on; other dynamic-light mods are unaffected.
 *
 * <p>Runtime flags (=false: off): bons_and_furious.hexereiEmptyLightScanFastPath, bons_and_furious.hexereiDynamicLightsOff.
 * -Dbons_and_furious.hexereiEmptyLightScanFastPath.count=true counts the fast answers (offline proofs only; off, the hot
 * path has no counter). No shadow mode: the premise is a count kept under the set's own lock (proven with concurrent
 * writers and readers offline).
 */
public final class HexLights {
    static final String FAST_KEY = "hexerei_empty_light_scan_fast_path", OFF_KEY = "hexerei_dynamic_lights_off";
    public static volatile boolean fastPath = flag("hexereiEmptyLightScanFastPath");
    public static volatile boolean lightsOff = flag("hexereiDynamicLightsOff");
    static final boolean COUNT = Boolean.getBoolean("bons_and_furious.hexereiEmptyLightScanFastPath.count");
    /** Offline statistics (only with COUNT): scans answered at once, size copies written. */
    public static long fastAnswers, mirrorWrites;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** LightManager.dynamicLightSources.size(), written inside the set's write lock after every change; the set starts empty. */
    private static volatile int sources;
    private static boolean announcedFast, announcedOff;

    private HexLights() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** Called inside LightManager's write lock, right before it is released, by each method that changes the set. */
    public static void mirror(int size) {
        sources = size;
        if (COUNT) mirrorWrites++;
    }

    /** The current copy of the set's size (offline proofs). */
    public static int mirrored() {
        return sources;
    }

    /** True: the scan may answer 0.0 at once (the set is empty). */
    public static boolean noSources() {
        if (!fastPath || sources != 0) return false;
        if (COUNT) fastAnswers++;
        if (!announcedFast) announce(true);
        return true;
    }

    /** LightManager.shouldUpdateDynamicLight's answer with the switch: false while it is on, else Hexerei's own. */
    public static boolean shouldUpdate(boolean original) {
        if (!lightsOff) return original;
        if (!announcedOff) announce(false);
        return false;
    }

    private static synchronized void announce(boolean fast) {
        if (fast && !announcedFast) {
            announcedFast = true;
            LOGGER.info("Bons and Furious: {}: Hexerei's dynamic light scans answer at once while it has no light source", FAST_KEY);
        } else if (!fast && !announcedOff) {
            announcedOff = true;
            LOGGER.info("Bons and Furious: {}: Hexerei's bundled dynamic lights are off", OFF_KEY);
        }
    }
}
