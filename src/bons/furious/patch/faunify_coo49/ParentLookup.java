package bons.furious.patch.faunify_coo49;

import bons.furious.mixin.faunify_coo49.LevelEntityGetterAccessor;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch faunify_millipede_parent_uuid_lookup (Faunify 1.3.3, client; our pack's 1.1.3 has no millipede).
 *
 * A millipede body segment finds the segment in front of it through getParentSegment: the cached parent while it lives;
 * otherwise, on the client, every entity in a box 50 blocks around the segment (Level.getEntities with a predicate on
 * the UUID). That runs from the segment's own tick and again from its renderer (which walks the chain of parents to pick
 * the texture), every tick and every frame while the parent is missing: whenever a chunk loads, and for as long as the head
 * is beyond the client's tracking range.
 *
 * Exact variant: the client level's entity lookup by UUID (the same visible entities the box search walks: the client's
 * entity sections are all accessible) gives the one candidate; it is accepted only when the box search would have
 * reached it: its section lies in the section range the search walks (x and z widened by 2 blocks, y down by 4, as
 * EntitySectionStorage does) and its bounding box intersects the box. Otherwise the answer is "none", as the search's. The
 * documented difference: two client entities with the parent's UUID (vanilla logs a duplicate-UUID warning) may resolve
 * to the other one (the lookup keeps the first registered, the search returns the first in section order). Runtime flag
 * -Dbons_and_furious.faunifyMillipedeParentUuidLookup=false. Shadow mode (.shadow=true): the search runs too and the two
 * answers are compared (WARN at most 20 mismatches). Faunify is MIT; no code of it is carried.
 */
public final class ParentLookup {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.faunifyMillipedeParentUuidLookup", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.faunifyMillipedeParentUuidLookup.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private ParentLookup() {
    }

    /** The box search's answer from the UUID lookup, or null when the search itself must run. */
    public static List<Entity> find(Level level, AABB box, UUID parent) {
        if (!enabled || parent == null || level == null || !level.f_46443_) return null;
        Entity e = ((LevelEntityGetterAccessor) level).bons$entityGetter().m_142694_(parent);
        if (!announced) announce();
        return e != null && reached(e, box) ? List.of(e) : List.of();
    }

    /** True when EntitySectionStorage.getEntities(box) would test this entity and its box intersects. */
    static boolean reached(Entity e, AABB box) {
        BlockPos p = e.m_20183_();
        int sx = SectionPos.m_123171_(p.m_123341_()), sy = SectionPos.m_123171_(p.m_123342_()), sz = SectionPos.m_123171_(p.m_123343_());
        if (sx < SectionPos.m_175552_(box.f_82288_ - 2.0) || sx > SectionPos.m_175552_(box.f_82291_ + 2.0)) return false;
        if (sy < SectionPos.m_175552_(box.f_82289_ - 4.0) || sy > SectionPos.m_175552_(box.f_82292_ + 0.0)) return false;
        if (sz < SectionPos.m_175552_(box.f_82290_ - 2.0) || sz > SectionPos.m_175552_(box.f_82293_ + 2.0)) return false;
        return e.m_20191_().m_82381_(box);
    }

    public static void shadow(List<Entity> ours, List<Entity> search, UUID parent) {
        SHADOW_CHECKS.incrementAndGet();
        Entity a = ours.isEmpty() ? null : ours.get(0), b = search.isEmpty() ? null : search.get(0);
        if (a != b) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: faunify_millipede_parent_uuid_lookup shadow mismatch #{}: parent {}: lookup {}, search {}", n, parent, a, b);
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: faunify_millipede_parent_uuid_lookup: a millipede segment finds a missing parent by its UUID instead of a 101-block box search");
    }
}
