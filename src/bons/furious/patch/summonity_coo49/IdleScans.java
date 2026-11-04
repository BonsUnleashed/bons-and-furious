package bons.furious.patch.summonity_coo49;

import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch summonity_idle_target_scan_interval (Summonity 0.1.7.1, server side; kind change, off by
 * default, value: ticks between searches, default 4 = COO's, range 1-100). Ours; no Summonity code is carried. Idea:
 * Collections Of Optimizations (summonity.idleTargetScanInterval), idea text only.
 *
 * An idle summoned minion (no commanded target, no current valid target) searches every Mob in its owner's minion range on
 * every tick for the nearest valid one (SummonityIdleScanMixin wraps that one search call in each of the 20 minion
 * classes). With the switch on, the search runs only when (game time + the minion's entity id) is a multiple of N, which
 * staggers the minions over the ticks; on other ticks it answers an empty list and the minion keeps no target until its
 * next scan tick. Commanded and current targets are found as before (the search is never reached for them). Trade-off: an
 * idle minion reacts to a new target up to N-1 ticks later. N = 1 searches every tick. Value:
 * PureConfig.intValue("summonity_idle_target_scan_interval", 4, 1, 100), read once on the switch-on path. Flag:
 * -Dbons_and_furious.summonityIdleTargetScanInterval=false.
 */
public final class IdleScans {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.summonityIdleTargetScanInterval", "true"));
    /** Searches left out (answered with an empty list) and searches made. */
    public static final AtomicLong SKIPPED = new AtomicLong();
    public static final AtomicLong SEARCHED = new AtomicLong();
    private static volatile boolean announced;

    private IdleScans() {
    }

    /** The value line, read on first use while the switch is on. */
    private static final class Interval {
        static final int N = PureConfig.intValue("summonity_idle_target_scan_interval", 4, 1, 100);
    }

    /** The scan interval in use (for the offline proof). */
    public static int interval() {
        return Interval.N;
    }

    /** The wrapped idle target search. */
    public static List<?> search(Entity minion, Level level, Class<?> type, AABB box, Predicate<?> test, Operation<List<?>> original) {
        if (!enabled || minion == null) return original.call(level, type, box, test);
        int n = Interval.N;
        if (n <= 1 || Math.floorMod(level.m_46467_() + minion.m_19879_(), n) == 0) {
            SEARCHED.incrementAndGet();
            return original.call(level, type, box, test);
        }
        SKIPPED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: summonity_idle_target_scan_interval applies (idle summoned minions look for targets every {} ticks)", n);
        }
        return List.of();
    }
}
