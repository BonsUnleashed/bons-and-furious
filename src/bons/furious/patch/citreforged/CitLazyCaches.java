package bons.furious.patch.citreforged;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch citreforged_lazy_stack_caches (CIT Reforged 1.0.2, MIT; client).
 *
 * CIT Reforged's four ItemStack mixins (item, armor, elytra, enchantment types) each add a final field initialised with a
 * new CITCache object (and a new bound method reference for it) to every ItemStack constructor, on every ItemStack the
 * client JVM builds (copies, recipe and tooltip work, the integrated server's stacks), although only stacks that are
 * drawn ever read a cache, and most of them only one or two of the four. The switch skips those four constructions
 * (CitLazyCachesMixin) and builds the same cache, bound to the same type container's getRealTimeCIT, the first time CIT
 * asks the stack for it, in a field of our own; CIT's field then stays null and CIT reads the cache only through the
 * getter that returns ours. A cache is not touched between the constructor and its first get(): its first get() finds
 * the same state (no cached CIT, stamp 0) either way, and every later call sees the same object.
 */
public final class CitLazyCaches {
    /** Runtime switch (read at each ItemStack construction). -Dbons_and_furious.citLazyStackCaches=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.citLazyStackCaches", "true"));
    /** Probe counters (plain longs, approximate under threads): caches built on first use, per type item/armor/elytra/enchantment. */
    public static long createdItem, createdArmor, createdElytra, createdEnchantment;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private CitLazyCaches() {
    }

    public static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: citreforged_lazy_stack_caches builds CIT Reforged's item stack caches on first use");
        }
    }
}
