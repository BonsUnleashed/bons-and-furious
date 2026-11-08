package bons.furious.patch.cnb_coo49;

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
 * Bons and Furious, group cnb_coo49 (Creatures and Beasts: Continued 1.7.17 for Minecraft 1.21.1, LGPL-3.0; server side;
 * since 1.0.49): the helper of five trade-off switches (all off by default, kind "change"). Every cactem and friendly
 * sporeling goal named below asks the level for every entity of one class in a large box on every goal check (the goal
 * selector asks every other tick); the switches let a goal reuse what its last search said for a number of checks instead:
 *
 * <ul>
 * <li>cnb_cactem_become_elder_scan_interval (BecomeElderGoal.canUse, every adult non-elder cactem: all cactems in a
 * 65 x 33 x 65 box, true when none is an elder): after a search that found an elder, the next N - 1 checks answer
 * "elder near" (false) without searching. Noticeable: a cactem whose elder has just died or left can take up to N - 1
 * checks longer to become an elder itself. Value cnb_cactem_become_elder_scan_interval.value (default 20).</li>
 * <li>cnb_cactem_follow_elder_scan_interval (FollowElderGoal.canUse, every non-elder cactem: all cactems in a 65 x 9 x 65
 * box, true only for an elder 20 blocks away or more): after a search that gave false (no elder, or the closest one nearer
 * than 20 blocks, the normal village case), the next N - 1 checks answer false without searching. Noticeable: a cactem
 * can start following an elder up to N - 1 checks later. Value default 10.</li>
 * <li>cnb_cactem_heal_scan_reuse (HealGoal.cactemNeedsHeal, every elder, from canUse, canContinueToUse and tick: all
 * cactems in a 33 x 9 x 33 box, true when a non-elder is at half health or less): the goal keeps its last search's list
 * for the next N - 1 calls, filtered by the current box with the search's own intersects test (a cactem that left drops
 * out at once); elder state and health are read live; a removed cactem in the list or a level change searches again at
 * once. Noticeable: a hurt cactem walking into an elder's range can be seen up to N - 1 calls later. Value default 10.</li>
 * <li>cnb_cactem_trade_scan_interval (TradeGoal.canUse, elders without a target: all item entities in a 33 x 7 x 33 box,
 * the first totem of undying item is walked to): after a search that found none, the next N - 1 checks that would search answer
 * false without searching; a search that finds one is never throttled. Noticeable: a totem dropped near an elder can
 * be noticed up to N - 1 checks later. Value default 10.</li>
 * <li>cnb_sporeling_convert_scan_interval (ConvertItemGoal.canUse, friendly sporelings not sitting: the same item search,
 * dirt or cursed enchanted items): as the trade switch. Value default 10.</li>
 * </ul>
 *
 * A value of 1 is the original behaviour. Values are read through PureConfig only when the switch's hook first runs (each
 * hook exists only while its switch is on). Runtime flags (emergency off, the original on every call):
 * -Dbons_and_furious.cnbCactemBecomeElderScanInterval=false, ...cnbCactemFollowElderScanInterval, ...cnbCactemHealScanReuse,
 * ...cnbCactemTradeScanInterval, ...cnbSporelingConvertScanInterval.
 * A skipped search still counts one "getEntities" on the level's profiler, as the search would.
 * Creatures and Beasts is LGPL-3.0; no code of it is carried (the hooks decide when its own searches run).
 */
public final class CnbScans {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    public static volatile boolean becomeElder = flag("cnbCactemBecomeElderScanInterval");
    public static volatile boolean followElder = flag("cnbCactemFollowElderScanInterval");
    public static volatile boolean heal = flag("cnbCactemHealScanReuse");
    public static volatile boolean trade = flag("cnbCactemTradeScanInterval");
    public static volatile boolean convert = flag("cnbSporelingConvertScanInterval");

    private static volatile boolean announcedElder, announcedFollow, announcedHeal, announcedTrade, announcedConvert;

    private CnbScans() {
    }

    private static boolean flag(String camel) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camel, "true"));
    }

    /** Checks per search of cnb_cactem_become_elder_scan_interval (read on first use, only while the switch is on). */
    public static final class BecomeElder {
        public static final int N = PureConfig.intValue("cnb_cactem_become_elder_scan_interval", 20, 1, 200);

        private BecomeElder() {
        }
    }

    /** Checks per search of cnb_cactem_follow_elder_scan_interval. */
    public static final class FollowElder {
        public static final int N = PureConfig.intValue("cnb_cactem_follow_elder_scan_interval", 10, 1, 200);

        private FollowElder() {
        }
    }

    /** Calls per search of cnb_cactem_heal_scan_reuse. */
    public static final class Heal {
        public static final int N = PureConfig.intValue("cnb_cactem_heal_scan_reuse", 10, 1, 200);

        private Heal() {
        }
    }

    /** Checks per search of cnb_cactem_trade_scan_interval. */
    public static final class Trade {
        public static final int N = PureConfig.intValue("cnb_cactem_trade_scan_interval", 10, 1, 200);

        private Trade() {
        }
    }

    /** Checks per search of cnb_sporeling_convert_scan_interval. */
    public static final class Convert {
        public static final int N = PureConfig.intValue("cnb_sporeling_convert_scan_interval", 10, 1, 200);

        private Convert() {
        }
    }

    /** One "getEntities" on the level's profiler for a search that was left out (the search would count it). */
    public static void countSkipped(Level level) {
        if (level != null) level.getProfiler().incrementCounter("getEntities");
    }

    /**
     * cnb_cactem_heal_scan_reuse: the cactems the heal search would answer: the goal's kept list (filtered by the current
     * box, none removed, same level, fewer than N - 1 uses) or a new search, which is kept.
     */
    public static List<?> healMembers(CactemHealScanHolder goal, Level level, Class<?> type, AABB box, Operation<List<?>> original) {
        if (!heal || Heal.N <= 1) return original.call(level, type, box);
        List<?> kept = goal.bons$cnbCoo49HealList();
        if (kept != null && goal.bons$cnbCoo49HealLevel() == level && goal.bons$cnbCoo49HealUses() < Heal.N - 1) {
            ArrayList<Object> out = new ArrayList<>(kept.size());
            boolean removed = false;
            for (Object o : kept) {
                Entity e = (Entity) o;
                if (e.isRemoved() || e.level() != level) {
                    removed = true;
                    break;
                }
                if (e.getBoundingBox().intersects(box)) out.add(e);
            }
            if (!removed) {
                goal.bons$cnbCoo49KeepHeal(kept, level, goal.bons$cnbCoo49HealUses() + 1);
                countSkipped(level);
                return out;
            }
        }
        List<?> fresh = original.call(level, type, box);
        goal.bons$cnbCoo49KeepHeal(fresh, level, 0);
        if (!announcedHeal) announce(3);
        return fresh;
    }

    /** First use of a switch: one INFO line naming its number. */
    public static void announce(int which) {
        synchronized (CnbScans.class) {
            switch (which) {
                case 1 -> {
                    if (announcedElder) return;
                    announcedElder = true;
                    LOGGER.info("Bons and Furious: cnb_cactem_become_elder_scan_interval: cactems look for an elder every {} goal checks after finding one", BecomeElder.N);
                }
                case 2 -> {
                    if (announcedFollow) return;
                    announcedFollow = true;
                    LOGGER.info("Bons and Furious: cnb_cactem_follow_elder_scan_interval: cactems look for an elder to follow every {} goal checks while none is to be followed", FollowElder.N);
                }
                case 3 -> {
                    if (announcedHeal) return;
                    announcedHeal = true;
                    LOGGER.info("Bons and Furious: cnb_cactem_heal_scan_reuse: cactem elders reuse their heal search for {} calls", Heal.N);
                }
                case 4 -> {
                    if (announcedTrade) return;
                    announcedTrade = true;
                    LOGGER.info("Bons and Furious: cnb_cactem_trade_scan_interval: cactem elders look for a dropped totem every {} goal checks while none is found", Trade.N);
                }
                default -> {
                    if (announcedConvert) return;
                    announcedConvert = true;
                    LOGGER.info("Bons and Furious: cnb_sporeling_convert_scan_interval: friendly sporelings look for items to convert every {} goal checks while none is found", Convert.N);
                }
            }
        }
    }

    public static boolean announcedElder() {
        return announcedElder;
    }

    public static boolean announcedFollow() {
        return announcedFollow;
    }

    public static boolean announcedTrade() {
        return announcedTrade;
    }

    public static boolean announcedConvert() {
        return announcedConvert;
    }
}
