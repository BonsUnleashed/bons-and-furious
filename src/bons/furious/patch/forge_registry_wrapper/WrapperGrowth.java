package bons.furious.patch.forge_registry_wrapper;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch forge_registry_wrapper_growth (Forge 47.4.16 and 47.4.28, both sides; fastutil 8.5.9, the version
 * Minecraft 1.20.1 ships). No Forge or fastutil code here.
 *
 * Forge keeps a NamespacedWrapper (NamespacedDefaultedWrapper for defaulted registries, which calls the same method) next
 * to every Forge-managed registry. Each registration that reaches it (ForgeRegistry.add -> NamespacedWrapper.Factory.onAdd
 * -> onAdded, for the ACTIVE stage and for intrusive registries while staging) runs
 * holdersById.size(Math.max(holdersById.size(), id + 1)) and then holdersById.set(id, holder). holdersById is a fastutil
 * ObjectArrayList made with capacity 256. ObjectArrayList.size(n) with n above the backing array's length reallocates the
 * array to exactly n slots (ObjectArrays.forceCapacity) and copies every element, so a registry that gains its ids one by
 * one copies its whole holder list once per registration: about n * n / 2 element copies for n entries (32,810 items and
 * 22,997 blocks here; NamespacedWrapper.onAdded was 8.4 % of the dedicated server's boot allocation and 8.3 % of the
 * client's in our 1.0.31 recordings: 2.6 and 5.3 GB of Object[] copies).
 *
 * Here, just before that size(n) call, when the list is exactly an ObjectArrayList, n is above its current size and n is at
 * most 2^24, ensureCapacity(the next power of two >= n) runs; then the size(n) call runs unchanged. ensureCapacity on an
 * ObjectArrayList that does not wrap a caller's array only replaces the backing array with a larger new Object[] holding
 * the same first size() elements (the slots beyond size() are null in every ObjectArrayList and in the new array); size(n)
 * then finds room and only null-fills the new slots, exactly as it does after its own reallocation. So the list has the
 * same size and the same elements after every call, and set(id, holder), get, stream and every exception behave the same;
 * only the length of the private backing array differs (at most twice the slots: about 260 KB more for the item registry).
 * Nothing reads that length: holdersById is private to NamespacedWrapper, which only calls size(), get(), set() and
 * stream() on it, and no installed mod's mixin reads the field (mixin index). Anything else (a shrink or no growth,
 * another list class, n above 2^24) runs the original call alone.
 */
public final class WrapperGrowth {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.forgeRegistryWrapperGrowth=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.forgeRegistryWrapperGrowth", "true"));
    /** Above this size the original call runs alone (no rounding up to a much larger array). */
    static final int MAX_ROUNDED = 1 << 24;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announced;

    private WrapperGrowth() {
    }

    /** Before holdersById.size(newSize) in NamespacedWrapper.onAdded: grow the backing array geometrically (capacity only). */
    public static void ahead(ObjectList<?> list, int newSize) {
        if (!enabled || list == null || list.getClass() != ObjectArrayList.class || newSize > MAX_ROUNDED || newSize <= list.size()) return;
        ((ObjectArrayList<?>) list).ensureCapacity(capacityFor(newSize));
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: forge_registry_wrapper_growth grows Forge's registry holder lists in powers of two instead of one slot per registration");
        }
    }

    /** The next power of two >= n (n >= 1). */
    static int capacityFor(int n) {
        return n <= 1 ? n : Integer.highestOneBit(n - 1) << 1;
    }
}
