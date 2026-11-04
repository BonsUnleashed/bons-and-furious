package bons.furious.patch.darkdoppelganger_coo49;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch darkdoppelganger_single_minion_add (Dark Doppelganger 9.9.0, All Rights Reserved; server;
 * gameplay fix, off by default). Ours; no Dark Doppelganger code is carried. Idea: Collections Of Optimizations
 * (darkdoppelganger.fixDoubleMinionSpawn), idea text only.
 *
 * DarkDoppelgangerEntity.summonMinions has the setup block of each minion (tag, name, name visible, the SpawnWeak flag) and
 * its level.addFreshEntity pasted twice. Forge (47.4.28) posts EntityJoinLevelEvent for an added entity BEFORE the entity
 * manager checks its UUID, so the second add of the same minion makes every join listener see that minion join a second
 * time (difficulty scalers, trait and loot mods can apply themselves twice; e.g. Armageddon's hard mode queues its health
 * multiplier twice) and then logs "UUID of added entity already exists". The setup lines are idempotent and stay.
 *
 * Gameplay fix (off by default): the second add of a minion whose first add succeeded is left out (SingleMinionAddMixin):
 * join listeners see each minion join once and the warning is gone; the minion, its position, name, tags and target are
 * the same. A first add that failed (a listener cancelled the join) keeps its second attempt as shipped. Flag:
 * -Dbons_and_furious.darkdoppelgangerSingleMinionAdd=false.
 */
public final class DoppelMinions {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.darkdoppelgangerSingleMinionAdd", "true"));
    /** Second adds left out (for the harness). */
    public static final AtomicLong SKIPPED = new AtomicLong();
    private static volatile boolean announced;

    private DoppelMinions() {
    }

    public static boolean skipSecondAdd() {
        if (!enabled) return false;
        SKIPPED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: darkdoppelganger_single_minion_add applies (each summoned minion joins the level once; off by default)");
        }
        return true;
    }
}
