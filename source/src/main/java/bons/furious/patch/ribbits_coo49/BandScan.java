package bons.furious.patch.ribbits_coo49;

import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch ribbits_band_scan_interval (Ribbits 1.21.1-NeoForge-4.1.6, server).
 *
 * RibbitPlayMusicGoal (every ribbit, both canUse and tick) asks the level for every ribbit in a 129 x 33 x 129 block box
 * around it, on every call, to find a playing ribbit whose band it can join: a walk over every entity section in that box
 * (9 x 3 x 9 sections) per ribbit per goal evaluation, in a ribbit village with a dozen ribbits.
 *
 * Trade-off (off by default): each goal keeps the list of ribbits its last box search found and reuses it for the next
 * N - 1 calls; the band state (who is playing, who leads the band) is still read live off those ribbits, a ribbit that has
 * left the box is dropped at once (the search's own box test, on the current box), and the list is searched again at
 * once when one of its ribbits was removed or the goal's ribbit changed level. What a player can notice: a ribbit that
 * walks into range can take up to N - 1 calls longer to be seen as a band to join, and with two bands in range the one
 * a ribbit joins can differ (the kept list keeps the order of its search). N = 1 is the original. The value is
 * PureConfig's ribbits_band_scan_interval.value (default 10, COO's default; 1..200).
 * Runtime flag -Dbons_and_furious.ribbitsBandScanInterval=false (the original search on every call).
 * Ribbits (LGPLv3): no code of it is carried (the hook decides when its own search runs).
 */
public final class BandScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ribbitsBandScanInterval", "true"));
    /** Calls per search, counting the search itself; read once, on the switch-on path (this class loads with the hook). */
    public static final int INTERVAL = PureConfig.intValue("ribbits_band_scan_interval", 10, 1, 200);
    private static volatile boolean announced;

    private BandScan() {
    }

    /** The ribbits a band search would answer: the kept list (current box, none removed) or a new search (kept). */
    public static List<?> neighbours(BandScanHolder goal, Level level, Class<?> type, AABB box, Operation<List<?>> original) {
        if (!enabled || INTERVAL <= 1) return original.call(level, type, box);
        List<?> band = goal.bons$band();
        if (band != null && goal.bons$bandLevel() == level && goal.bons$bandUses() < INTERVAL - 1) {
            ArrayList<Object> out = new ArrayList<>(band.size());
            boolean removed = false;
            for (Object o : band) {
                Entity e = (Entity) o;
                if (e.isRemoved()) {
                    removed = true;
                    break;
                }
                if (e.getBoundingBox().intersects(box)) out.add(e);
            }
            if (!removed) {
                goal.bons$keepBand(band, level, goal.bons$bandUses() + 1);
                return out;
            }
        }
        List<?> fresh = original.call(level, type, box);
        goal.bons$keepBand(fresh, level, 0);
        if (!announced) announce();
        return fresh;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: ribbits_band_scan_interval: ribbits search for a band to join every {} goal calls", INTERVAL);
    }
}
