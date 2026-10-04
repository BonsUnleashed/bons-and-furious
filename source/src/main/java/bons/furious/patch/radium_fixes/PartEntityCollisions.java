package bons.furious.patch.radium_fixes;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import me.jellysquid.mods.lithium.common.entity.EntityClassGroup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch radium_part_entity_collisions (Radium, LGPL-3.0; 1.21.1 tested build
 * radium-mc1.21.1-0.13.1+git.4994e83; both sides). No Radium code here.
 *
 * Fix. Vanilla collision (EntityGetter.getEntityCollisions, Level.noCollision) asks the level for every entity in the box
 * that the moving entity can collide with: NeoForge's Level.getEntities(Entity, AABB, Predicate) walks the entity
 * sections AND then every PartEntity of the level (Level.getPartEntities(); parts are not stored in sections). Radium's
 * entity.collisions.movement / intersection replace that with WorldHelper.getEntitiesForCollision, whose shortcut (taken
 * whenever the moving entity does not override canCollideWith, i.e. players and nearly all mobs) reads only the sections'
 * "hard" class group (classes overriding canBeCollidedWith). A PartEntity with a hard box is therefore never collided
 * with (on 1.20.1: Untamed Wilds' whale shark, baleen whale and anaconda parts, Cataclysm's Old Netherite Monstrosity
 * parts, Alex's Mobs giant squid parts, Unusual Prehistory's Brachiosaurus parts).
 *
 * The switch appends, after Radium's section result, the parts vanilla's loop would return: each part of
 * Level.getPartEntities() in its iteration order that is not the moving entity, whose box intersects the (already
 * 1.0E-7-inflated) query box and that is not a spectator - restricted to part classes in the same hard class group,
 * which drops only parts whose canBeCollidedWith() is Entity's constant false and that vanilla's predicate rejects anyway.
 * Radium's own filter (canCollideWith, or canBeCollidedWith without a moving entity: in appendEntityCollisions and in the
 * iterator of getEntityWorldBorderCollisionIterable) then decides exactly as vanilla's predicate. Nothing changes when the
 * level has no part entities (one isEmpty() call) or none intersects.
 *
 * Cost: like vanilla's own loop, one box test per part entity of the level per lookup; Radium had skipped that loop.
 *
 * Ported to 1.21.1: NeoForge 21.1's Level.getEntities has the same part loop as Forge 47 (p != except, box intersects,
 * predicate) and Radium 0.13.1's shortcut is unchanged apart from the name of the "custom canCollideWith" group
 * (CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE, which now also holds wind charges); the hard group is still
 * NoDragonClassGroup.BOAT_SHULKER_LIKE_COLLISION. Radium 0.13.1 moved its movement collisions to appendEntityCollisions
 * (same filter as the iterable, guarded). Not covered, as on 1.20.1 where it did not exist: Radium 0.13.1's new
 * entity.collisions.intersection.EntityViewMixin redirects the lookup inside EntityGetter.getEntityCollisions to
 * WorldHelper.getOtherEntitiesForCollision, a second copy of the shortcut that also drops part entities (and does not apply
 * the predicate); fixing it would need its own exactness argument, so this port leaves it to Radium.
 */
public final class PartEntityCollisions {
    /** Runtime switch. -Dbons_and_furious.radiumPartEntityCollisions=false turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.radiumPartEntityCollisions", "true"));
    /** Shadow mode for rigs: every shortcut query is also answered by vanilla's Level.getEntities and the sets compared. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.radiumPartEntityCollisions.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /**
     * Counters for probes: part entities added to a result (rare: only hard parts in reach); shortcut queries in a level
     * with part entities, counted only in shadow mode (the client and the integrated server would share the counter).
     */
    public static final AtomicLong PARTS_ADDED = new AtomicLong(), QUERIES_WITH_PARTS = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private PartEntityCollisions() {
    }

    /**
     * Radium's section result for this query plus the hard part entities vanilla's loop returns, in vanilla's order
     * (sections first, then getPartEntities() order). {@code sectionEntities} is the fresh list Radium just built.
     */
    public static List<Entity> withHardParts(List<Entity> sectionEntities, Level level, Entity colliding,
                                             EntityClassGroup hardGroup, AABB box) {
        Collection<PartEntity<?>> parts = level.getPartEntities();
        if (!parts.isEmpty()) {
            if (SHADOW) QUERIES_WITH_PARTS.incrementAndGet();
            for (PartEntity<?> part : parts) {
                // vanilla's order (not the moving entity, box intersects, then the predicate); the class-group test only
                // drops parts whose canBeCollidedWith() is Entity's constant false
                if (part != colliding && part.getBoundingBox().intersects(box) && hardGroup.contains(part.getClass()) && !part.isSpectator()) {
                    sectionEntities.add(part);
                    PARTS_ADDED.incrementAndGet();
                    if (!announced) {
                        announced = true;
                        LOGGER.info("Bons and Furious: radium_part_entity_collisions: {} is a hard part entity; collisions with it "
                                + "are checked as in vanilla", part.getClass().getName());
                    }
                }
            }
        }
        if (SHADOW) shadow(sectionEntities, level, colliding, box);
        return sectionEntities;
    }

    /**
     * Compares what Radium's filter keeps from {@code ours} (canCollideWith / canBeCollidedWith) with vanilla's
     * getEntities(colliding, box, predicate) of EntityGetter.getEntityCollisions, as identity sets.
     */
    static void shadow(List<Entity> ours, Level level, Entity colliding, AABB box) {
        SHADOW_CHECKS.incrementAndGet();
        Set<Entity> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity e : ours) {
            if (colliding == null ? e.canBeCollidedWith() : colliding.canCollideWith(e)) kept.add(e);
        }
        Predicate<Entity> predicate = colliding == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(colliding::canCollideWith);
        Set<Entity> vanilla = Collections.newSetFromMap(new IdentityHashMap<>());
        vanilla.addAll(level.getEntities(colliding, box, predicate));
        if (!kept.equals(vanilla) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: radium_part_entity_collisions shadow check: {} at {}: Radium+fix kept {} entities, vanilla {}",
                    colliding, box, kept.size(), vanilla.size());
        }
    }
}
