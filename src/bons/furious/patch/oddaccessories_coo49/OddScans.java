package bons.furious.patch.oddaccessories_coo49;

import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious trade-off switches oddaccessories_cloak_aggro_scan_interval, oddaccessories_meal_toy_scan_gate and
 * oddaccessories_cloak_action_bar_interval (Odd Accessories 1.1.6, All Rights Reserved; server side: both curio ticks return
 * on client levels before their searches). Ours; no Odd Accessories code is carried. Idea: Collections Of Optimizations
 * (oddaccessories.cloakAggroScanInterval, oddaccessories.gateMealToyScan), idea text only. All three are of kind "change"
 * and ship off.
 *
 * Fiberglass Cloak: while a sneaking wearer is eligible, FiberglassCloakItem.onCurioTick searches every Mob within 12 blocks
 * every tick and clears each one's aggro toward the wearer (target, last attacker, anger, brain memories). With
 * cloakAggroSearch the search runs on the wearer's scan ticks only (game time + the wearer's entity id is a multiple of N,
 * the value line, default 4 as in Collections Of Optimizations); on the other ticks it answers an empty list, so that tick
 * clears nothing. The
 * 4-block eligibility count, the invisibility refresh and the action-bar messages run every tick as shipped. Trade-off: a
 * mob can keep targeting the cloaked wearer up to N-1 ticks longer.
 *
 * Collectible Meal Toy: CollectibleMealToyItem.onCurioTick searches every Mob within the toy range every tick; for baby
 * undead it applies Weakness (20 ticks), clears the target, repaths toward the wearer on the wearer's repath ticks (every
 * 10th) and looks at / marks them on the global particle pulse (every 80th game tick). With toySearch the search runs only
 * on those repath or pulse ticks (the method's own two flags, read from its locals); on other ticks it answers an empty list.
 * Trade-off: a baby undead keeps its target up to 9 ticks longer and its Weakness is refreshed every 10 ticks instead of
 * every tick (the 20-tick duration still covers the gap, and the effect update packets come every 10 ticks).
 *
 * Fiberglass Cloak indicator (switch oddaccessories_cloak_action_bar_interval, Idea: Collections Of Optimizations
 * (oddaccessories.cloakActionBarInterval), idea text only): onCurioTick sends the wearer an action-bar text every tick, "(⭘)"
 * while sneaking with too many mobs close and "(-)" while cloaked (a chat packet each; the vanilla action bar keeps a text at
 * full strength for 40 of its 60 ticks). With cloakMessage an indicator that differs from the one last sent to the player
 * is sent at once, the same one again only when at least N ticks (the value line, default 10 as in Collections Of
 * Optimizations) have passed since it was sent. Trade-off: after another action-bar text replaced it the indicator can
 * come back up to N-1 ticks late, and after the cloak stops it fades up to N-1 ticks earlier.
 *
 * Flags: -Dbons_and_furious.oddaccessoriesCloakAggroScanInterval=false, -Dbons_and_furious.oddaccessoriesMealToyScanGate=false,
 * -Dbons_and_furious.oddaccessoriesCloakActionBarInterval=false.
 */
public final class OddScans {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean cloakEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oddaccessoriesCloakAggroScanInterval", "true"));
    public static volatile boolean toyEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oddaccessoriesMealToyScanGate", "true"));
    public static volatile boolean barEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oddaccessoriesCloakActionBarInterval", "true"));
    /** The two indicator call sites of FiberglassCloakItem.onCurioTick. */
    public static final byte BAR_BUSY = 1, BAR_CLOAKED = 2;
    private static volatile boolean cloakAnnounced, toyAnnounced, barAnnounced;

    /** The indicator interval, read on the first indicator of a switched-on cloak (so only while the switch is on). */
    private static final class Bar {
        static final int N = PureConfig.intValue("oddaccessories_cloak_action_bar_interval", 10, 1, 100);
    }

    /** Ticks between re-sends of an unchanged cloak indicator (harness and log). */
    public static int barInterval() {
        return Bar.N;
    }

    /** The wrapped Player.displayClientMessage(text, true) calls of FiberglassCloakItem.onCurioTick. */
    public static void cloakMessage(Player player, Component text, boolean actionBar, Operation<Void> original, byte site) {
        if (!barEnabled || !(player instanceof OddCloakBarDuck d)) {
            original.call(player, text, actionBar);
            return;
        }
        int n = Bar.N;
        long now = player.m_9236_().m_46467_(), last = d.bons$oddaccessoriesCoo49BarTick();
        if (n > 1 && d.bons$oddaccessoriesCoo49BarSite() == site && now >= last && now - last < n) {
            if (!barAnnounced) {
                barAnnounced = true;
                LOGGER.info("Bons and Furious: oddaccessories_cloak_action_bar_interval applies (the Fiberglass Cloak indicator is re-sent every {} ticks)", n);
            }
            return;
        }
        original.call(player, text, actionBar);
        d.bons$oddaccessoriesCoo49BarSent(site, now);
    }

    private OddScans() {
    }

    /** The interval, read on the first scan of a switched-on cloak (so only while the switch is on). */
    private static final class Every {
        static final int N = PureConfig.intValue("oddaccessories_cloak_aggro_scan_interval", 4, 1, 100);
    }

    /** Ticks between the cloak's clear-aggro searches (harness and log). */
    public static int cloakInterval() {
        return Every.N;
    }

    /** True when the wearer's clear-aggro search runs on this game tick. */
    public static boolean cloakScanTick(long gameTime, int wearerId, int n) {
        return n <= 1 || Math.floorMod(gameTime + wearerId, (long) n) == 0;
    }

    /** The wrapped 12-block Level.getEntitiesOfClass(Mob, box) of FiberglassCloakItem.onCurioTick. */
    @SuppressWarnings({"rawtypes"})
    public static List cloakAggroSearch(Level level, Class<?> type, AABB box, Operation<List> original, LivingEntity wearer) {
        if (!cloakEnabled || wearer == null || cloakScanTick(level.m_46467_(), wearer.m_19879_(), Every.N)) {
            return original.call(level, type, box);
        }
        if (!cloakAnnounced) {
            cloakAnnounced = true;
            LOGGER.info("Bons and Furious: oddaccessories_cloak_aggro_scan_interval applies (the Fiberglass Cloak clears aggro every {} ticks)", Every.N);
        }
        return List.of();
    }

    /** The wrapped Level.getEntitiesOfClass(Mob, box) of CollectibleMealToyItem.onCurioTick. */
    @SuppressWarnings({"rawtypes"})
    public static List toySearch(Level level, Class<?> type, AABB box, Operation<List> original, boolean particleTick, boolean repath) {
        if (!toyEnabled || particleTick || repath) return original.call(level, type, box);
        if (!toyAnnounced) {
            toyAnnounced = true;
            LOGGER.info("Bons and Furious: oddaccessories_meal_toy_scan_gate applies (the Collectible Meal Toy searches on its repath and pulse ticks)");
        }
        return List.of();
    }
}
