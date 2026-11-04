package bons.furious.patch.simplyswordsoverhaul_coo49;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The two per-player static maps Simply Swords Overhaul 1.0.4's sword mixins merge into Simply Swords' item classes:
 * StarsEdgeSwordItem.savedPositions (player UUID -> the spot a Stars Edge recall returns to) and
 * WhisperwindSwordItem.DASH_HIT_MAP (player UUID -> the entities a Whisperwind dash has hit). Found once, by reflection
 * (they exist only where Simply Swords Overhaul's mixins applied); a missing or changed field is reported once and left
 * alone. Server thread only, like the maps themselves.
 */
final class SsoMaps {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static Map<?, ?> savedPositions, dashHits;
    private static boolean looked;

    private SsoMaps() {
    }

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        savedPositions = staticMap("net.sweenus.simplyswords.item.custom.StarsEdgeSwordItem", "savedPositions");
        dashHits = staticMap("net.sweenus.simplyswords.item.custom.WhisperwindSwordItem", "DASH_HIT_MAP");
    }

    private static Map<?, ?> staticMap(String owner, String name) {
        try {
            Class<?> c = Class.forName(owner, true, SsoMaps.class.getClassLoader());
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(null);
            if (v instanceof Map<?, ?> m) return m;
            LOGGER.warn("Bons and Furious: {}.{} is not a map ({}); Simply Swords Overhaul's entries there are left alone", owner, name, v == null ? "null" : v.getClass().getName());
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: cannot reach {}.{} ({}); Simply Swords Overhaul's entries there are left alone", owner, name, t.toString());
        }
        return null;
    }

    /** Empties both maps; the number of entries removed. */
    static int clearAll() {
        look();
        int n = 0;
        for (Map<?, ?> m : new Map<?, ?>[]{savedPositions, dashHits}) {
            if (m == null) continue;
            n += m.size();
            m.clear();
        }
        return n;
    }

    /** Removes the player's Whisperwind dash hit list, as the end of a Whisperwind dash does. */
    static void forgetDashHits(UUID player) {
        look();
        if (dashHits != null) dashHits.remove(player);
    }
}
