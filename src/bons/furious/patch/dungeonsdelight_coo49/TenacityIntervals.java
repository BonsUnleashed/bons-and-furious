package bons.furious.patch.dungeonsdelight_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch dungeonsdelight_tenacity_per_player_interval (Dungeons Delight 1.2.10 / 1.3.1, server). Gameplay
 * fix, OFF by default.
 *
 * Tenacity heals its holder on an interval of 3 x their food level (TenacityEffect.getInterval), but Dungeons Delight keeps
 * that interval in ONE field of the one shared TenacityEffect object, written by every player whose Tenacity starts or
 * heals. With two or more players under Tenacity, each heals on the interval of whoever wrote last.
 *
 * With the switch on, the interval Dungeons Delight computes for a player (at those same two places) is also kept on that
 * player (a field on LivingEntity, TenacityIntervalHolder), and MobEffectInstance.tick asks TenacityEffect's
 * isDurationEffectTick with the holder's own interval: duration % (own - amplifier * 2) == 0, true when that divisor is 0
 * (as dungeonsdelight_tenacity_zero_interval_guard does). Entities without an own interval (mobs, the client, a player
 * whose Tenacity was loaded from disk and has not healed yet) use the shared field exactly as before. What changes: each
 * player heals on their own food level's interval. Runtime flag: -Dbons_and_furious.dungeonsdelightTenacityPerPlayer=false.
 * No Dungeons Delight code is carried.
 */
public final class TenacityIntervals {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dungeonsdelightTenacityPerPlayer", "true"));
    private static volatile boolean announced;

    private TenacityIntervals() {
    }

    /** isDurationEffectTick with the holder's own interval (never 0 when recorded: getInterval returns 1 or 3 x food). */
    public static boolean tick(int duration, int amplifier, int ownInterval) {
        if (!announced) announce();
        int divisor = ownInterval - amplifier * 2;
        return divisor == 0 || duration % divisor == 0;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: dungeonsdelight_tenacity_per_player_interval: Tenacity heals each player on their own interval");
    }
}
