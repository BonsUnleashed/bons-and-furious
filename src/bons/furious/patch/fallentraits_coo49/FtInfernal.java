package bons.furious.patch.fallentraits_coo49;

import dev.xkmc.l2library.util.math.MathHelper;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious trade-off switch fallentraits_infernal_follow_range_cap (Fallen Traits & Relics 1.0.0,
 * LGPL-2.1-or-later; server side; kind "change", ships off). Ours. Idea: Collections Of Optimizations
 * (fallentraits.clampInfernalFollowRange), idea text only.
 *
 * The Infernal trait adds +2048 to a mob's FOLLOW_RANGE (TraitManager.addAttribute with the modifier name
 * fallen_traits_infernal_follow_range; vanilla caps the attribute at 2048). Vanilla then sizes every path search region by
 * the follow range (PathNavigation.createPath builds a PathNavigationRegion of +-2048 blocks: ~66,000 chunk lookups per path)
 * and NearestAttackableTargetGoal.findTarget searches a box 4,097 blocks wide for non-player targets.
 *
 * clamp() gives min(range, 256) for a mob whose FOLLOW_RANGE instance holds the Infernal modifier (only asked when the range
 * is above 256, so other mobs pay one comparison): the path search and its region, and the target-search box, stop at 256
 * blocks. What differs: an Infernal mob's paths toward a target farther than 256 blocks end at the closest point reached
 * (it re-paths from there), and it no longer picks new non-player targets beyond 256 blocks; keeping a target
 * (canContinueToUse), player targeting through TargetingConditions and the trait's own far-player chase keep the full
 * range.
 *
 * Flag: -Dbons_and_furious.fallentraitsInfernalFollowRangeCap=false.
 */
public final class FtInfernal {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fallentraitsInfernalFollowRangeCap", "true"));
    public static final double CAP = 256.0;
    private static volatile boolean announced;

    private FtInfernal() {
    }

    private static final class Id {
        static final UUID INFERNAL = MathHelper.getUUIDFromString("fallen_traits_infernal_follow_range");
    }

    /** True when the mob's FOLLOW_RANGE carries the Infernal trait's modifier. */
    public static boolean infernal(Mob mob) {
        AttributeInstance range = mob.m_21051_(Attributes.f_22277_);
        return range != null && range.m_22111_(Id.INFERNAL) != null;
    }

    /** min(range, 256) for an Infernal mob, else range. */
    public static double clamp(Mob mob, double range) {
        if (range <= CAP || !enabled || mob == null || !infernal(mob)) return range;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fallentraits_infernal_follow_range_cap applies (Infernal mobs path and search for targets within 256 blocks)");
        }
        return CAP;
    }

    /** The float form for PathNavigation.createPath's follow-range argument. */
    public static float clampPath(Mob mob, float range) {
        return range <= (float) CAP ? range : (float) clamp(mob, range);
    }
}
