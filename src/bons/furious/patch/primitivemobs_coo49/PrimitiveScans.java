package bons.furious.patch.primitivemobs_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch primitivemobs_skip_villager_goblin_avoid (Primitive Mobs 1.0.4, CC BY 3.0; server;
 * since 1.0.49; trade-off, off by default).
 *
 * Primitive Mobs hands every villager that joins a server level a vanilla AvoidEntityGoal (priority 1) against its
 * goblins; the goal selector then asks that goal every other tick, and each ask searches the level for goblins in a box
 * of about 17 x 8 x 17 blocks around the villager, for every villager, as long as it lives. With the switch on that goal
 * is not added (the call that adds it is left out; nothing else of the join handler changes). What a player can notice:
 * villagers no longer run from goblins.
 * Runtime flag -Dbons_and_furious.primitivemobsSkipVillagerGoblinAvoid=false (the goal is added again for villagers
 * that join afterwards). No code of Primitive Mobs is carried.
 */
public final class PrimitiveScans {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.primitivemobsSkipVillagerGoblinAvoid", "true"));
    private static volatile boolean announced;

    private PrimitiveScans() {
    }

    /** True when the join handler's goal must be added (switch off), false when it is left out. */
    public static boolean addVillagerGoblinAvoid() {
        if (!enabled) return true;
        if (!announced) announce();
        return false;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: primitivemobs_skip_villager_goblin_avoid: villagers no longer get Primitive Mobs' flee-from-goblins goal");
    }
}
