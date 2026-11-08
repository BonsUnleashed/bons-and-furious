package bons.furious.patch.mowziesmobs_c49;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches mowziesmobs_umvuthana_leader_cache and mowziesmobs_elokosa_leader_reuse (Mowzie's Mobs; 1.21.1
 * tested build: Mowzie's Mobs 1.21.1-1.8.2), both sides, since 1.0.49; trade-offs, off by default. Ideas: Collections Of
 * Optimizations (mowziesmobs.cacheUmvuthanaLeader, mowziesmobs.reuseKnownLeader), idea text only. Our own logic; no Mowzie's
 * Mobs code (custom licence, wrappers only).
 *
 * EntityUmvuthanaFollower.getLeader and EntityElokosaFollower.getLeader list every entity of the leader's class in the
 * follower's box inflated by 32 blocks (a 64-block cube) and return the one with the leader's id. The Umvuthana that follow
 * a player call it twice per tick, the Elokosa followers whenever their restriction is asked (hasRestriction,
 * getRestrictCenter: the leap goal asks for every block it considers).
 *
 * What changes: a follower keeps the leader its last search returned and answers it again while that entity is still in
 * the world (not removed), in the follower's level, not a spectator, still carries the leader id, and its box still
 * meets the follower's 64-block cube: the tests Mowzie's search applies to it. Otherwise the search runs as before and its
 * answer is kept. The one difference: Mowzie's search lists only entities in parts of the world that are loaded for
 * entities (and Valkyrien Skies can change what it lists for entities on ships), so a kept leader can stay the follower's
 * leader for a while after the search would have stopped seeing it.
 *
 * -Dbons_and_furious.mowziesmobsUmvuthanaLeaderCache=false / -Dbons_and_furious.mowziesmobsElokosaLeaderReuse=false run
 * every search.
 */
public final class LeaderCache {
    public static final String UMVUTHANA_KEY = "mowziesmobs_umvuthana_leader_cache", ELOKOSA_KEY = "mowziesmobs_elokosa_leader_reuse";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean umvuthanaEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsUmvuthanaLeaderCache", "true"));
    public static volatile boolean elokosaEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsElokosaLeaderReuse", "true"));
    private static volatile boolean umvuthanaAnnounced, elokosaAnnounced;

    private LeaderCache() {
    }

    /** True when the kept leader passes the tests Mowzie's search applies to it (see the class comment). */
    public static boolean keeps(LivingEntity follower, Optional<UUID> id, LivingEntity known) {
        if (known == null || id == null || id.isEmpty()) return false;
        if (known.isRemoved() || known.level() != follower.level() || known.isSpectator()) return false;
        if (!id.get().equals(known.getUUID())) return false;
        return known.getBoundingBox().intersects(follower.getBoundingBox().inflate(32.0, 32.0, 32.0));
    }

    public static void announceUmvuthana() {
        if (!umvuthanaAnnounced) {
            umvuthanaAnnounced = true;
            LOGGER.info("Bons and Furious: {} applies (Umvuthana followers keep the leader they found while it stays near)", UMVUTHANA_KEY);
        }
    }

    public static void announceElokosa() {
        if (!elokosaAnnounced) {
            elokosaAnnounced = true;
            LOGGER.info("Bons and Furious: {} applies (Elokosa followers keep the leader they found while it stays near)", ELOKOSA_KEY);
        }
    }
}
