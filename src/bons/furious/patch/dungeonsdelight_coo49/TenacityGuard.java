package bons.furious.patch.dungeonsdelight_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch dungeonsdelight_tenacity_zero_interval_guard (Dungeons Delight 1.2.10 / 1.3.1, both sides).
 *
 * TenacityEffect.isDurationEffectTick(duration, amplifier) returns duration % (applyInterval - amplifier * 2) == 0, where
 * applyInterval is 3 x the food level of the player that last wrote it (food 0 -> 1; 20 before any write, and always 20 on
 * the client). Whenever 3 x food == 2 x amplifier (Tenacity IV at food 2, VII at food 4, X at food 6, ...; on the client
 * Tenacity XI at any food) the divisor is 0 and the % throws ArithmeticException out of MobEffectInstance.tick ->
 * LivingEntity.tickEffects (which only catches ConcurrentModificationException): a server tick crash ("Ticking entity").
 *
 * Fix: for that one input the method answers true (an interval of zero means "every tick", the limit of the formula as the
 * divisor shrinks); every other input runs Dungeons Delight's own expression. Nothing else changes. Runtime flag:
 * -Dbons_and_furious.dungeonsdelightTenacityZeroGuard=false. Dungeons Delight's licence is a custom one: no code of it is
 * carried; the guard is our own test.
 */
public final class TenacityGuard {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dungeonsdelightTenacityZeroGuard", "true"));
    private static volatile boolean announced;

    private TenacityGuard() {
    }

    /** True when Dungeons Delight's own expression would divide by zero (the guard then answers true). */
    public static boolean zeroDivisor(int applyInterval, int amplifier) {
        if (!enabled || applyInterval - amplifier * 2 != 0) return false;
        if (!announced) announce(applyInterval, amplifier);
        return true;
    }

    private static synchronized void announce(int interval, int amplifier) {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: dungeonsdelight_tenacity_zero_interval_guard: Tenacity {} with interval {} would divide by zero; treated as an effect tick instead of crashing",
                amplifier + 1, interval);
    }
}
