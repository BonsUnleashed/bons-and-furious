package bons.furious.patch.companions_owner_coo49;

import dev.xylonity.companions.common.entity.CompanionSummonEntity;
import dev.xylonity.companions.common.util.Util;
import dev.xylonity.companions.mixin.LevelAccessor;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch companions_owner_walk (Companions 1.3.6 for Minecraft 1.21.1, both sides).
 *
 * Companions decides whether two entities are on the same side (Util.areTeammates and Util.areEntitiesLinked, called by
 * its companions' target goals, projectiles, summons and tesla behaviours) by walking owner chains: computePlayer(entity)
 * follows projectile owner -> tamed or ownable owner until a player, with a new HashSet of the UUIDs it has seen (a loop
 * ends the walk with no player); areEntitiesLinked runs areTeammates (two such walks) and then collectOwners for each side
 * with two more new HashSets each (visited entities, and every UUID met: the entities' own, owner UUIDs, a summon's second
 * owner), and intersects the two owner sets. Six HashSets, their tables and one node per UUID for every check.
 *
 * The switch walks the same chains with the same calls, in the same order, and keeps the seen UUIDs in small inline sets
 * instead: computePlayer is a tail call in Companions, so here it is the same walk as a loop with eight UUID locals; a
 * longer walk moves them into a UuidSet (an open-addressing UUID table, no node per entry) and goes on there (no depth
 * limit). collectOwners is the same depth-first recursion over a small per-check object: the visited set and the first
 * side's owner set as eight-entry arrays that spill into UuidSets; for the second side only "is this UUID one of the
 * first side's owners" is kept, which is what the intersection asks. Membership is UUID.equals, as in a HashSet (a null
 * UUID equals only null). Every getUUID, getOwner,
 * getOwnerUUID, getSecondOwnerUUID, level, getPlayerByUUID and entity-getter call happens as often and in the same order
 * as in Companions' own walk, with the same casts, so results, exceptions and every side effect of those calls (a
 * projectile caching its owner, any mod's own getOwner) are the same. areTeammates itself is called as before.
 * Runtime flag -Dbons_and_furious.companionsOwnerWalk=false. Companions is GPL-3.0; this walk is our own code.
 */
public final class OwnerWalk {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.companionsOwnerWalk", "true"));
    private static volatile boolean announced;

    private OwnerWalk() {
    }

    private static boolean same(UUID id, UUID seen) {
        return id == null ? seen == null : id.equals(seen);
    }

    /**
     * The seen UUIDs of a walk that outgrew its inline slots: an open-addressing table of UUID references (linear probing,
     * HashMap's hash spread, kept at most half full), membership by UUID.equals with null as its own entry, as in a HashSet;
     * no node per entry. Only add and contains are needed: nothing iterates it, so its order cannot show.
     */
    static final class UuidSet {
        private UUID[] table = new UUID[64];
        private int size;
        private boolean hasNull;

        /** Set.add: true when id was not yet in the set. */
        boolean add(UUID id) {
            if (id == null) {
                if (this.hasNull) return false;
                this.hasNull = true;
                return true;
            }
            UUID[] t = this.table;
            int mask = t.length - 1;
            int i = spread(id.hashCode()) & mask;
            for (UUID u; (u = t[i]) != null; i = (i + 1) & mask) if (id.equals(u)) return false;
            t[i] = id;
            if (++this.size * 2 > t.length) this.grow();
            return true;
        }

        boolean contains(UUID id) {
            if (id == null) return this.hasNull;
            UUID[] t = this.table;
            int mask = t.length - 1;
            for (int i = spread(id.hashCode()) & mask; t[i] != null; i = (i + 1) & mask) if (id.equals(t[i])) return true;
            return false;
        }

        private void grow() {
            UUID[] old = this.table, t = new UUID[old.length * 2];
            int mask = t.length - 1;
            for (UUID u : old) {
                if (u == null) continue;
                int i = spread(u.hashCode()) & mask;
                while (t[i] != null) i = (i + 1) & mask;
                t[i] = u;
            }
            this.table = t;
        }

        private static int spread(int h) {
            return h ^ (h >>> 16);
        }
    }

    /** Util.computePlayer(entity): the player at the end of its owner chain, or null (no owner, a loop, not a player). */
    public static Player player(Entity entity) {
        UUID s0 = null, s1 = null, s2 = null, s3 = null, s4 = null, s5 = null, s6 = null, s7 = null;
        UuidSet more = null;
        int n = 0;
        Player found = null;
        for (Entity e = entity; e != null; ) {
            UUID id = e.getUUID();
            if (more != null) {
                if (!more.add(id)) break;
            } else if (n > 0 && same(id, s0) || n > 1 && same(id, s1) || n > 2 && same(id, s2) || n > 3 && same(id, s3)
                    || n > 4 && same(id, s4) || n > 5 && same(id, s5) || n > 6 && same(id, s6) || n > 7 && same(id, s7)) {
                break;
            } else {
                switch (n++) {
                    case 0 -> s0 = id;
                    case 1 -> s1 = id;
                    case 2 -> s2 = id;
                    case 3 -> s3 = id;
                    case 4 -> s4 = id;
                    case 5 -> s5 = id;
                    case 6 -> s6 = id;
                    case 7 -> s7 = id;
                    default -> {
                        more = new UuidSet();
                        more.add(s0);
                        more.add(s1);
                        more.add(s2);
                        more.add(s3);
                        more.add(s4);
                        more.add(s5);
                        more.add(s6);
                        more.add(s7);
                        more.add(id);
                    }
                }
            }
            if (e instanceof Player p) {
                found = p;
                break;
            }
            if (e instanceof Projectile projectile) e = projectile.getOwner();
            else if (e instanceof OwnableEntity ownable) e = ownable.getOwner();
            else break;
        }
        if (!announced) announce();
        return found;
    }

    /** Util.areEntitiesLinked(a, b). */
    public static boolean linked(Entity a, Entity b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        if (Util.areTeammates(a, b)) return true;
        Walk walk = new Walk();
        walk.collect(a);
        walk.secondSide();
        walk.collect(b);
        return walk.common;
    }

    /** One link check's sets: the walk's visited UUIDs and the first side's owner UUIDs, 8 inline entries each. */
    private static final class Walk {
        private final UUID[] visited = new UUID[8], owners = new UUID[8];
        private int visitedCount, ownerCount;
        private UuidSet moreVisited, moreOwners;
        private boolean second, common;

        /** Companions' collectOwners(e, owners, visited), call for call. */
        void collect(Entity e) {
            if (e == null) return;
            if (!visit(e.getUUID())) return;
            owner(e.getUUID());
            if (e instanceof TamableAnimal tamable) {
                UUID ownerId = tamable.getOwnerUUID();
                if (ownerId != null) owner(ownerId);
                if (e instanceof CompanionSummonEntity summon) {
                    UUID secondId = summon.getSecondOwnerUUID();
                    if (secondId != null) owner(secondId);
                }
                collect(tamable.getOwner());
            }
            if (e instanceof Projectile projectile) collect(projectile.getOwner());
            if (e instanceof OwnableEntity ownable) {
                UUID ownerId = ownable.getOwnerUUID();
                if (ownerId != null) {
                    owner(ownerId);
                    Entity next = e.level().getPlayerByUUID(ownerId);
                    if (next == null && e.level() instanceof LevelAccessor level) next = (Entity) level.companions$getEntities().get(ownerId);
                    collect(next);
                }
            }
        }

        /** The second side's walk: a new visited set; its owner UUIDs are only matched against the first side's. */
        void secondSide() {
            second = true;
            visitedCount = 0;
            moreVisited = null;
        }

        /** visited.add(id). */
        private boolean visit(UUID id) {
            if (moreVisited != null) return moreVisited.add(id);
            for (int i = 0; i < visitedCount; i++) if (same(id, visited[i])) return false;
            if (visitedCount < 8) {
                visited[visitedCount++] = id;
                return true;
            }
            moreVisited = new UuidSet();
            for (UUID u : visited) moreVisited.add(u);
            moreVisited.add(id);
            return true;
        }

        /** owners.add(id): kept for the first side, compared for the second. */
        private void owner(UUID id) {
            if (second) {
                if (!common && isFirstSideOwner(id)) common = true;
                return;
            }
            if (isFirstSideOwner(id)) return;
            if (moreOwners != null) {
                moreOwners.add(id);
            } else if (ownerCount < 8) {
                owners[ownerCount++] = id;
            } else {
                moreOwners = new UuidSet();
                for (UUID u : owners) moreOwners.add(u);
                moreOwners.add(id);
            }
        }

        private boolean isFirstSideOwner(UUID id) {
            if (moreOwners != null) return moreOwners.contains(id);
            for (int i = 0; i < ownerCount; i++) if (same(id, owners[i])) return true;
            return false;
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: companions_owner_walk: Companions walks owner chains without building new sets");
    }
}
