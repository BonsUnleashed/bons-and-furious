package bons.furious.patch.uniqueaccessories_coo49;

import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch uniqueaccessories_rose_repath_interval (Unique Accessories 1.3.3, All Rights Reserved; server
 * side; kind change, off by default). Ours; no Unique Accessories code is carried. Idea: Collections Of Optimizations
 * (uniqueaccessories.roseRepathInterval), idea text only.
 *
 * <p>While a player holds or wears a Rose of Temptation, RoseOfTemptationEvents.onPlayerTick tells every lured mob farther
 * than 1.5 blocks away to path to the player again on every tick (PathNavigation.moveTo(player, 1.1)), and the game
 * computes a new path whenever the player's block position has changed. With the switch on a lured mob gets a new path
 * only when its navigation has finished or has no path, or when the configured number of ticks has passed since its last
 * one. Trade-off: lured mobs follow the player's position from up to that many ticks ago (a mob that lost its path still
 * gets a new one at once). Value: ticks between new paths, default 10 (COO's default), range 1-200; 1 is the shipped
 * behaviour. Runtime flag: -Dbons_and_furious.uniqueaccessoriesRoseRepathInterval=false.
 */
public final class UaRepath {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.uniqueaccessoriesRoseRepathInterval", "true"));
    /** Ticks between new paths (read once, only while the switch is on: the mixin applies only then). */
    static final int INTERVAL = PureConfig.intValue("uniqueaccessories_rose_repath_interval", 10, 1, 200);
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Game time of each navigation's last moveTo from this handler (server thread only; weak keys, identity). */
    private static final Map<PathNavigation, long[]> LAST = new WeakHashMap<>();
    private static volatile boolean announced;
    /** Statistics (plain, racy): moveTo calls left out. */
    public static long skipped;

    private UaRepath() {
    }

    /** The interval in use (the harness reads it). */
    public static int interval() {
        return INTERVAL;
    }

    /** In place of onPlayerTick's navigation.moveTo(player, speed) (its result is dropped by the handler). */
    public static boolean moveTo(PathNavigation navigation, Entity target, double speed, Operation<Boolean> original) {
        int n = INTERVAL;
        if (!enabled || n <= 1 || navigation == null || target == null) return original.call(navigation, target, speed);
        long now = target.m_9236_().m_46467_();                    // the level's game time
        long[] last = LAST.get(navigation);
        if (last == null || navigation.m_26571_() || now - last[0] >= n || now < last[0]) {    // isDone: finished or no path
            if (last == null) LAST.put(navigation, last = new long[1]);
            last[0] = now;
            return original.call(navigation, target, speed);
        }
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: uniqueaccessories_rose_repath_interval applies (mobs lured by the Rose of Temptation get a new path every {} ticks)", n);
        }
        return false;
    }
}
