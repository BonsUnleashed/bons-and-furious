package bons.furious.patch.castercuriosbonus_coo49;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.SlotContext;

/**
 * Bons and Furious, group castercuriosbonus_coo49 (Caster Curios Bonus 1.20.1-1.1.1, GPLv3; Curios + Iron's Spellbooks).
 * Ours; no Caster Curios Bonus code is carried. Two gameplay fixes, both off by default:
 *
 * caster_curios_bonus_mana_to_health_server_only. Idea: Collections Of Optimizations
 * (castercuriosbonus.serverOnlyManaToHealth), idea text only. Curios ticks a worn trinket on both sides; the Mind to
 * Matter necklace (unlike its sibling, the Eureka charm) never asks which side it is on, so the client also counts the
 * necklace's refresh counter and every 10 counts takes the max-health modifier off its own copy of the player and puts
 * one back computed from the client's copy of max mana and the client's own COMMON config (which the server never
 * sends). In single player both threads count the same counter (a field of the one Item object), so the refresh comes
 * at an irregular pace. With the switch, on a client level the tick ends where it ends for a wearer that is not a
 * player (the SlotContext.entity() read answers null to the necklace's "instanceof Player" test): the client's max
 * health follows the server's synced value only, and the server's counter is counted by the server alone.
 *
 * caster_curios_bonus_per_wearer_refresh. Idea: Collections Of Optimizations (castercuriosbonus.perPlayerCurioThrottle),
 * idea text only. Both trinkets keep their "every 20 / every 10 ticks" countdown in a field of the Item, which exists
 * once for the whole game, so every wearer's tick counts the same counter and whoever's tick reaches the limit gets the
 * refresh: with 2, 4, 5 or 10 wearers ticking in a fixed order (20 / 10 divisible by the number of wearers) the same
 * wearer always wins and the others are never refreshed. With the switch a wearer is due when
 * floorMod(entity.tickCount + entity.getId(), interval) == 0 (its own tick counter, spread by entity id), the item's
 * counter is neither read for the test nor written, and the refresh itself runs as shipped: every wearer is refreshed
 * exactly every 20 (Eureka) / 10 (Mind to Matter) of its own ticks however many others wear one.
 *
 * Runtime flags (-D...=false turns one off): bons_and_furious.casterCuriosBonusManaToHealthServerOnly,
 * .casterCuriosBonusPerWearerRefresh.
 */
public final class CcbRefresh {
    public static final String SERVER_ONLY_KEY = "caster_curios_bonus_mana_to_health_server_only";
    public static final String PER_WEARER_KEY = "caster_curios_bonus_per_wearer_refresh";
    /** The trinkets' own refresh intervals (the constants their counter is compared with). */
    public static final int EUREKA_INTERVAL = 20, MIND_TO_MATTER_INTERVAL = 10;

    public static volatile boolean serverOnly = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.casterCuriosBonusManaToHealthServerOnly", "true"));
    public static volatile boolean perWearer = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.casterCuriosBonusPerWearerRefresh", "true"));
    /** Statistics for probes (plain fields): client necklace ticks ended early; per-wearer due answers. */
    public static long clientTicksEnded, dueAnswers;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;

    private CcbRefresh() {
    }

    private static void announce(int bit, String what) {
        synchronized (CcbRefresh.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}", what);
    }

    /** CcbMindToMatterServerOnlyMixin: the wearer the necklace's tick sees (null on a client level: the tick ends). */
    public static LivingEntity wearer(LivingEntity entity) {
        if (!serverOnly || entity == null) return entity;
        Level level = entity.m_9236_();
        if (level == null || !level.f_46443_) return entity;
        clientTicksEnded++;
        if ((announced & 1) == 0) announce(0, SERVER_ONLY_KEY + " applies (the Mind to Matter necklace refreshes its health bonus on the server only)");
        return null;
    }

    /** The per-wearer test: is this wearer due for its refresh on this tick? */
    public static boolean due(SlotContext slotContext, int interval) {
        LivingEntity e = slotContext == null ? null : slotContext.entity();
        if (e == null) return false;
        dueAnswers++;
        if ((announced & 2) == 0) announce(1, PER_WEARER_KEY + " applies (each wearer of a Caster Curios Bonus charm or necklace is refreshed on its own schedule)");
        return Math.floorMod(e.f_19797_ + e.m_19879_(), interval) == 0;
    }

    /** The value the trinket's counter test reads while the switch is on: at least the limit when due, else below it. */
    public static int counterFor(SlotContext slotContext, int interval) {
        return due(slotContext, interval) ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    }
}
