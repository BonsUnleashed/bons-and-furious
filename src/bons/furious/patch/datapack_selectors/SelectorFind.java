package bons.furious.patch.datapack_selectors;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_selector_find_loop (Minecraft 1.20.1 on Forge 47.4.16; server side incl. the integrated
 * server). Idea: Paper "Remove streams from hot code" (title only), idea text only.
 *
 * EntitySelector.findEntities runs every `@e`/`@a`/... selector result through stream().filter(feature check).toList().
 * EntitySelectorFindMixin walks the list itself, calling the same filter lambda once per entity in iteration order, and
 * builds the result with Stream.toList (so the class, unmodifiability and null tolerance are exactly vanilla's): the raw
 * list's own stream when nothing was dropped, else an array stream over the kept entities. The filter stage and its
 * spined buffer are not allocated. Same content, same order, same calls.
 *
 * -Dbons_and_furious.selectorFindLoop=false switches it off at run time.
 *
 * Shadow mode (-Dbons_and_furious.selectorFindLoop.shadow=true, for the in-game check): EntitySelectorFindMixin also runs
 * vanilla's own pipeline (stream().filter(the same lambda).toList()) over the same raw list and compares the two results
 * (class, size, identity and order); the switch's result is returned. SHADOW_CHECKS / SHADOW_MISMATCHES count the
 * comparisons; the first 20 mismatches are logged as WARN.
 */
public final class SelectorFind {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.selectorFindLoop", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.selectorFindLoop.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private SelectorFind() {
    }

    /** kept == null: nothing was dropped (the raw list itself, k == its size). */
    public static List<? extends Entity> toList(List<? extends Entity> raw, Entity[] kept, int k) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_selector_find_loop applies (entity selector results are filtered without a stream stage){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return kept == null ? raw.stream().toList() : Arrays.stream(kept, 0, k).toList();
    }

    /** Shadow mode: the switch's result against vanilla's pipeline over the same raw list. */
    public static void shadowCompare(List<? extends Entity> ours, List<? extends Entity> vanilla) {
        SHADOW_CHECKS.incrementAndGet();
        boolean same = ours.getClass() == vanilla.getClass() && ours.size() == vanilla.size();
        for (int i = 0; same && i < ours.size(); i++) same = ours.get(i) == vanilla.get(i);
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: vanilla_selector_find_loop shadow mismatch: {} of {} vs vanilla {} of {} (check {})",
                    ours.size(), ours.getClass().getName(), vanilla.size(), vanilla.getClass().getName(), SHADOW_CHECKS.get());
        }
    }
}
