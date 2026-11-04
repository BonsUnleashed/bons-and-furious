package bons.furious.patch.crittersandcompanions_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch crittersandcompanions_leash_render_empty_skip (Critters and Companions 2.3.5 and 2.7.1, client).
 *
 * SilkLeashRenderer.renderSilkLeash runs after every rendered living entity (2.7.1: LivingEntityRenderer and GeckoLib
 * renderers; 2.3.5: GeckoLib's render event) and loops over the entity's silk-leash holders: `for (LivingEntity e :
 * getLeashedByEntities())`. Nearly always that set is empty, and the loop still opens a HashSet iterator (one object)
 * per entity per frame. The switch hands the loop the shared empty iterator when the set is empty: the loop ends at
 * once, as it does over the set's own iterator; a non-empty set gets its own iterator, so nothing else changes.
 * Runtime flag -Dbons_and_furious.crittersandcompanionsLeashRenderEmptySkip=false. No Critters and Companions code is
 * carried (2.3.5 is MIT, 2.7.1 All Rights Reserved).
 */
public final class LeashSkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.crittersandcompanionsLeashRenderEmptySkip", "true"));
    private static volatile boolean announced;

    private LeashSkip() {
    }

    /** The loop's iterator: the shared empty one for an empty set, the set's own otherwise. */
    public static Iterator<?> iterator(Set<?> set, Operation<Iterator<?>> original) {
        if (enabled && set.isEmpty()) {
            if (!announced) announce();
            return Collections.emptyIterator();
        }
        return original.call(set);
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: crittersandcompanions_leash_render_empty_skip: the silk leash render pass no longer opens an iterator for entities nobody leashes");
    }
}
