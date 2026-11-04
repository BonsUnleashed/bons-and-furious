package bons.furious.patch.farmersdelight_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch farmersdelight_client_tempt_goal_skip (Farmer's Delight 1.3.4, client side of the listener).
 *
 * CommonEvents.onAnimalsJoinWorld listens to EntityJoinLevelEvent on both sides: for every mob of the horse-feed-tempted
 * tag and every rabbit that joins a level it streams the mob's goals for its vanilla TemptGoal's priority and, when there is
 * one, builds an Ingredient (a new item array) and a TemptGoal and adds the goal at that priority. On the client a mob's
 * goal selector never runs (Mob.serverAiStep, the only caller of GoalSelector.tick, runs on the server), and goals are not
 * synced, so the client copy's extra goal is built and never asked anything.
 *
 * Exact variant: the listener returns at once for a client level, so client copies of those mobs carry no Farmer's
 * Delight tempt goal; the server adds its goals exactly as before (the listener runs unchanged for a server level).
 * Runtime flag -Dbons_and_furious.farmersdelightClientTemptGoalSkip=false. Farmer's Delight is MIT; no code of it is
 * carried.
 */
public final class ClientTemptSkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.farmersdelightClientTemptGoalSkip", "true"));
    private static volatile boolean announced;

    private ClientTemptSkip() {
    }

    /** True when the listener should return: switch on and the level is a client level. */
    public static boolean skip(boolean clientSide) {
        if (!clientSide || !enabled) return false;
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: farmersdelight_client_tempt_goal_skip: Farmer's Delight no longer builds tempt goals for client copies of mobs");
    }
}
