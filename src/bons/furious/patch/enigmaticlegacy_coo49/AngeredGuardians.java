package bons.furious.patch.enigmaticlegacy_coo49;

import com.google.common.collect.Multimap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch enigmaticlegacy_angered_guardians_prune (Enigmatic Legacy 2.30.1, server side). Ours; no
 * Enigmatic Legacy code is carried. Idea: Collections Of Optimizations (enigmaticlegacy.pruneAngeredGuardians), idea text
 * only.
 *
 * Enigmatic Legacy keeps a static ArrayListMultimap from players to every guardian that ever targeted a cursed bearer of
 * the Heart of the Guardian (EnigmaticEventHandler.AGERED_GUARDIANS). Entries are added in onAttackTargetSet and the map is
 * cleared only when a server starts, so on a long-running server every guardian of a guardian farm stays referenced (with
 * its level, goals and data) until the next restart.
 *
 * Fix: before each put, values whose guardian is removed for good (Entity.RemovalReason.shouldDestroy: killed or
 * discarded) are dropped. The only reader is containsEntry(player, guardian) in the same handler, on a guardian whose
 * target is changing, i.e. a live one; Entity.equals compares entity ids, ids are never handed out twice in a game
 * session, and a destroyed entity never comes back, so no later answer changes. Guardians that were only unloaded are
 * kept, and player keys are never touched. Flag: -Dbons_and_furious.enigmaticlegacyAngeredGuardiansPrune=false.
 */
public final class AngeredGuardians {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.enigmaticlegacyAngeredGuardiansPrune", "true"));
    /** Values dropped so far (for the offline proof and rigs). */
    public static final AtomicLong PRUNED = new AtomicLong();
    private static volatile boolean announced;

    private AngeredGuardians() {
    }

    /** Drops the values of {@code map} that are entities removed for good (killed or discarded). */
    public static void prune(Multimap<?, ?> map) {
        if (!enabled || map == null || map.isEmpty()) return;
        int removed = 0;
        for (Iterator<?> it = map.values().iterator(); it.hasNext(); ) {
            Object v = it.next();
            if (v instanceof Entity e && e.m_213877_()) {
                Entity.RemovalReason why = e.m_146911_();
                if (why != null && why.m_146965_()) {
                    it.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            PRUNED.addAndGet(removed);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: enigmaticlegacy_angered_guardians_prune applies (Enigmatic Legacy no longer keeps killed guardians in its angered-guardian map)");
            }
        }
    }
}
