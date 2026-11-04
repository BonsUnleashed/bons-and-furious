package bons.furious.patch.relics_c49;

import bons.pure.config.PureConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch relics_essence_speed_cap (Relics 0.8.0.13, All Rights Reserved: injector only, no Relics code
 * carried; both sides, a gameplay fix that is off by default). Ours.
 *
 * What Relics does. The death and life essences (Holy Locket and friends) home in on their target with
 * moveTowardsTargetInArc: each tick their speed is set to (distance to the target) x (age in ticks x 0.005). After about
 * 200 ticks the step is longer than the distance, after about 400 ticks more than twice as long: an essence that has not
 * hit yet overshoots further on every tick and accelerates without bound (its hit test then loads chunks along an
 * ever longer path; the entity-section crash this used to cause is fixed by vanilla_entity_section_x_overflow).
 *
 * What the switch does. The speed is the smallest of Relics' own value, the cap (blocks per tick, `.value` line, default
 * 4.0 as Collections Of Optimizations chose) and the remaining distance to the target point Relics aims at (its centre
 * height), so an essence never overshoots and stops at its target. While Relics' value is below both, every step is the
 * same as before (same direction, same length, bit for bit).
 *
 * Both sides compute the movement (the client follows the target the server sends): the client and the server should use
 * the same setting, as for any gameplay switch.
 *
 * -Dbons_and_furious.relicsEssenceSpeedCap=false switches it off at run time (emergency off; the config key decides).
 */
public final class EssenceSpeed {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.relicsEssenceSpeedCap", "true"));
    /** Read once, on the switch-on path (this class loads only when the mixin is applied and an essence moves). */
    static final double MAX_SPEED = PureConfig.doubleValue("relics_essence_speed_cap", 4.0, 0.1, 64.0);
    private static volatile boolean announced;

    private EssenceSpeed() {
    }

    /** The argument of the Vec3.scale call in moveTowardsTargetInArc: Relics' speed, capped. */
    public static double speed(double relics, Entity essence, Vec3 targetPos) {
        if (!enabled) return relics;
        double remaining = essence.m_20182_().m_82554_(targetPos);
        double capped = Math.min(relics, Math.min(MAX_SPEED, remaining));
        if (capped != relics && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: relics_essence_speed_cap applies (Relics' death and life essences move at most {} blocks per tick and stop at their target)", MAX_SPEED);
        }
        return capped;
    }
}
