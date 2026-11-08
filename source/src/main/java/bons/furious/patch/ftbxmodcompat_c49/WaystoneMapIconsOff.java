package bons.furious.patch.ftbxmodcompat_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch ftbxmodcompat_waystone_map_icons_off (FTB XMod Compat 21.1.14 with Waystones, client; kind change,
 * off by default). Ours; no FTB XMod Compat code is carried.
 *
 * What FTB XMod Compat does. WaystonesCommon.mapWidgets is registered on FTB Chunks' MINIMAP and LARGE_MAP icon events
 * and adds every waystone the player knows in the current dimension. FTB Chunks' MapIcon.isVisible is true for such icons
 * on the minimap whatever their distance (WaystoneMapIcon, a StaticMapIcon, does not override it), so FTB Chunks draws every
 * known waystone on the minimap's rim every frame (a pose push, translate and scale and one textured quad draw each), sorts
 * them with the other icons every icon refresh, and lists them on the large map (where StaticMapIcon teleports to a marker
 * on the T key).
 *
 * What the switch does. With the switch on, mapWidgets returns at once. Trade-off (off by default): Waystones markers no
 * longer show on FTB Chunks' minimap and large map (so the large map's teleport on a waystone marker is gone too); every
 * other map icon, the waystones themselves and teleporting through them are unchanged. Client only, no data.
 *
 * -Dbons_and_furious.ftbxmodcompatWaystoneMapIconsOff=false switches it off at run time.
 */
public final class WaystoneMapIconsOff {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ftbxmodcompatWaystoneMapIconsOff", "true"));
    /** Icon events answered without waystone icons (harness and rig counter). */
    public static long skipped;
    private static volatile boolean announced;

    private WaystoneMapIconsOff() {}

    public static boolean skip() {
        if (!enabled) return false;
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: ftbxmodcompat_waystone_map_icons_off applies (no Waystones markers on FTB Chunks' maps)");
        }
        return true;
    }
}
