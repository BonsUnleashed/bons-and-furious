package bons.furious.patch.bowenchants_coo49;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches bowenchants_draw_speed_bows_only and bowenchants_strict_enhanced_power (Advanced Bow
 * Enchantments 1.2.0, both sides; kind change, gameplay fixes that are off by default). Advanced Bow Enchantments is All
 * Rights Reserved: none of its code is carried, the switches only change which value its unchanged code receives. Ours,
 * ideas from Collections Of Optimizations (bowenchants.drawSpeedBowsOnly, bowenchants.strictEnhancedPower), idea text only.
 *
 * Draw speed: BowEnchantsEvents.onLivingEntityUseItemTick hands every main-hand item use (eating, drinking, blocking, the
 * spyglass, horns) to its draw-speed adjustment, so its Fast Draw enchantments and the draw-speed attributes of other
 * mods (Too Many Bows, Apothic Attributes) speed those up too. With {@link #drawSpeedOff} the adjustment still runs as
 * shipped (every read it makes), and for an item whose use animation is neither the bow's nor the crossbow's the event's
 * own duration is answered instead of the adjusted one. Bows, crossbows and other items that use those animations get
 * exactly the shipped value.
 *
 * Enhanced Power: onEntityJoinLevel multiplies an arrow's base damage by the owner's main-hand Enhanced Power level on
 * every join (a chunk that loads the arrow again, a portal copy, which keeps its data) and marks the arrow as done
 * without ever reading that mark; any other projectile made while the owner merely holds an Enhanced Power item gets the
 * damage multiplier tag too. With {@link #enhancedPowerLevel} the handler's read of the main-hand Enhanced Power level
 * answers 0 (the handler then stops as for an unenchanted bow) when the projectile was loaded from disk, when an arrow
 * already carries the done mark, or when a projectile that is not an arrow is made while its owner is not drawing that
 * main-hand item. A freshly shot arrow gets the shipped multiplier once.
 *
 * Flags: -Dbons_and_furious.bowenchantsDrawSpeedBowsOnly=false / -Dbons_and_furious.bowenchantsStrictEnhancedPower=false.
 */
public final class BowFixes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean drawSpeedEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.bowenchantsDrawSpeedBowsOnly", "true"));
    public static volatile boolean enhancedPowerEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.bowenchantsStrictEnhancedPower", "true"));
    static final String APPLIED = "bowenchants.enhanced_power_arrow_applied";
    /** Uses kept at their duration and projectile joins answered 0 (plain counters for probes and the harness). */
    public static long keptDurations, deniedJoins;
    private static volatile boolean announcedDraw, announcedPower;

    private BowFixes() {
    }

    /** True when the draw-speed adjustment must not change this use: the used item draws neither as a bow nor as a crossbow. */
    public static boolean drawSpeedOff(ItemStack used) {
        if (!drawSpeedEnabled || used == null) return false;
        UseAnim a = used.m_41780_();
        if (a == UseAnim.BOW || a == UseAnim.CROSSBOW) return false;
        keptDurations++;
        if (!announcedDraw) {
            announcedDraw = true;
            LOGGER.info("Bons and Furious: bowenchants_draw_speed_bows_only applies (draw speed no longer shortens eating, drinking, blocking and other item uses)");
        }
        return true;
    }

    /** The handler's Enhanced Power level read: the shipped level, or 0 where the bonus does not belong (see the class text). */
    public static int enhancedPowerLevel(int level, ItemStack mainhand, EntityJoinLevelEvent event) {
        if (level <= 0 || !enhancedPowerEnabled) return level;
        Entity projectile = event.getEntity();
        boolean deny;
        if (event.loadedFromDisk()) {
            deny = true;
        } else if (projectile instanceof AbstractArrow) {
            deny = projectile.getPersistentData().m_128471_(APPLIED);   // the handler writes the ForgeData compound on this path anyway
        } else {
            Entity owner = projectile instanceof Projectile p ? p.m_19749_() : null;
            deny = !(owner instanceof LivingEntity le && le.m_6117_() && le.m_21211_() == mainhand);
        }
        if (!deny) return level;
        deniedJoins++;
        if (!announcedPower) {
            announcedPower = true;
            LOGGER.info("Bons and Furious: bowenchants_strict_enhanced_power applies (Enhanced Power is applied once per shot, and only to what the bow fires)");
        }
        return 0;
    }
}
