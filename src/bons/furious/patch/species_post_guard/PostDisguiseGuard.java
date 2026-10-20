package bons.furious.patch.species_post_guard;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix species_post_disguise_guard (Species 3.5, All Rights Reserved: no Species code is carried; client).
 * Idea: Collections Of Optimizations (species.fixDoubleRenderListener), idea text only; this narrower variant is ours.
 *
 * Species' ForgeClientEvents.livingEntityRenderer listens to the abstract RenderLivingEvent, so it runs for Pre and for Post.
 * For an entity wearing a Wicked Mask that holds an entity id (a disguise), its first branch cancels the event and draws the
 * disguise instead. Post is not cancelable: EventBus throws UnsupportedOperationException and the game stops ("Rendering
 * entity in world"). Vanilla never gets there - the same branch cancels Pre and LivingEntityRenderer then posts no Post - but
 * six mods here post a Post themselves from a Pre listener when they draw a carried or held entity (Alex's Caves riders,
 * the submarine and held mobs; Alex's Mobs; Cataclysm; Legendary Monsters grabs; Primal walrus rider and crocodile prey;
 * Unusual Prehistory riders and held mobs). Their listeners are registered on RenderLivingEvent.Pre itself and Species' on
 * the parent class, and EventBus runs an event's own listeners before its parent's at the same priority, so theirs always
 * comes first: a disguised entity carried by one of them throws in Species' handler.
 *
 * The fix: the branch's first test (the head item is a Wicked Mask) also requires the event to be a Pre. For every Pre event
 * and every Post event on which stock does not throw, the result is the same (the branch is skipped exactly when stock skips
 * it; the tests it no longer evaluates for Post only read state). Where stock throws, the branch is skipped and the handler's
 * remaining lines run as they do for every other Post.
 *
 * Runtime flag: -Dbons_and_furious.speciesPostDisguiseGuard=false keeps Species' test as it is. MASKED_POSTS counts the Post
 * events with a Wicked Mask on the head that the guard turned away (the crash candidates).
 */
public final class PostDisguiseGuard {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.speciesPostDisguiseGuard", "true"));
    public static final AtomicLong MASKED_POSTS = new AtomicLong();
    private static volatile boolean announced;

    private PostDisguiseGuard() {
    }

    /** The value of Species' "head item is a Wicked Mask" test, for this event. */
    public static boolean maskTest(boolean wearsMask, Object event) {
        if (!wearsMask || !enabled || event instanceof RenderLivingEvent.Pre) return wearsMask;
        MASKED_POSTS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: species_post_disguise_guard applies (a Post render event of a masked entity no longer reaches Species' cancel)");
        }
        return false;
    }
}
