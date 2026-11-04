package bons.furious.patch.iceandfire_c49;

import com.github.alexthe666.iceandfire.entity.EntityMutlipartPart;
import java.util.List;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch iceandfire_multipart_part_lean (Ice and Fire 2.1.13-1.20.1-beta-5, server logic, since 1.0.49).
 * Idea: Collections Of Optimizations (iceandfire.leanMultipartTick), idea text only. Our own code (LGPL-3.0 target).
 *
 * EntityMutlipartPart.collideWithNearbyEntities runs for every part of every Ice and Fire multipart mob (dragons, sea
 * serpents, death worms, ...) once per server tick, twice for slow-follow parts. Stock: the entities in the part's box
 * (Level.getEntities(part, box.inflate(0.2F, 0, 0.2F)), the float 0.2 widened to a double as in the bytecode), then the
 * parent (getParent(), a UUID lookup), then a stream over the list with two capturing lambdas: filter (not the parent, not
 * sharing a rider with it, not a multipart part, isPushable()) and forEach push(parent).
 *
 * What changes: the same list, the same parent lookup and then a plain loop that makes the same calls on each element in
 * list order: e != parent, sharesRider(parent, e), instanceof, isPushable(), push(parent). A sequential stream evaluates
 * filter and forEach element by element in encounter order, so the calls, their order and their arguments are those of
 * stock; only the stream pipeline and the two lambdas are not allocated. The parent is looked up once per call, as in
 * stock (no reuse across calls).
 *
 * -Dbons_and_furious.iceandfireMultipartPartLean=false runs the stock method. No shadow mode: the loop restates the
 * stream call for call (the harness compares the recorded calls of both).
 */
public final class MultipartLean {
    public static final String KEY = "iceandfire_multipart_part_lean";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfireMultipartPartLean", "true"));
    private static volatile boolean announced;

    private MultipartLean() {
    }

    /** collideWithNearbyEntities, as a loop. */
    public static void collide(EntityMutlipartPart part) {
        List<Entity> entities = part.m_9236_().m_45933_(part, part.m_20191_().m_82363_((double) 0.2F, 0.0, (double) 0.2F));
        Entity parent = part.getParent();
        if (parent != null) {
            for (Entity e : entities) {
                if (e != parent && !EntityMutlipartPart.sharesRider(parent, e) && !(e instanceof EntityMutlipartPart) && e.m_6094_()) e.m_7334_(parent);
            }
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Ice and Fire's multipart parts push nearby entities without a stream)", KEY);
        }
    }
}
