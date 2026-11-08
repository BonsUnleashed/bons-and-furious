package bons.furious.patch.moonlight_c49;

import net.mehvahdjukaar.moonlight.api.map.ExpandedMapData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch moonlight_empty_marker_refresh_skip (Moonlight Lib 1.21.1-3.8.1; both sides). Moonlight is under
 * the Supplementaries Team License; no Moonlight code is carried, only an early return in front of its handler. Ours, idea
 * from Collections Of Optimizations (moonlight.skipEmptyMapMarkerScan), idea text only.
 *
 * What Moonlight does: its MapDataMixin.checkCustomDeco runs at the end of every MapItemSavedData.checkBanners call. Vanilla
 * MapItem.update calls checkBanners once for every block column it samples while a player holds an unlocked map (about
 * 4,000 calls per tick at scale 0). Each run on a LevelAccessor builds two ArrayLists, walks the entry set of the map's
 * custom block markers, and hands both lists to forEach with a bound method reference, whatever the map holds.
 *
 * What the switch does: the handler returns at once while the map's custom block marker map (Moonlight's own public getter
 * ExpandedMapData.ml$getCustomMarkers(), which returns its final field) is empty. Why identical: with an empty map the
 * handler's loop runs zero times, nothing is added to either list, and forEach over an empty ArrayList calls nothing; the
 * two lists and the method references are never seen by anyone. A map with any marker runs Moonlight's handler as shipped.
 *
 * Flag: -Dbons_and_furious.moonlightEmptyMarkerRefreshSkip=false restores the handler on every call.
 */
public final class EmptyMarkers {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.moonlightEmptyMarkerRefreshSkip", "true"));
    private static volatile boolean announced;

    private EmptyMarkers() {
    }

    /** True when Moonlight's checkCustomDeco handler would do nothing for this map now (its marker map is empty). */
    public static boolean nothingToRefresh(Object mapData) {
        if (!enabled || !((ExpandedMapData) mapData).ml$getCustomMarkers().isEmpty()) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: moonlight_empty_marker_refresh_skip applies (maps without custom markers skip Moonlight's per-column marker refresh)");
        }
        return true;
    }
}
