package bons.furious.patch.create_logistics;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch create_mounted_slot_index (Create 6.0.8, MIT, both sides).
 *
 * A contraption's item storage is one MountedItemStorageWrapper (Forge's CombinedInvWrapper over every mounted chest,
 * vault, toolbox ...; trains use its subclass TrainCargoManager$CargoInvWrapper). Every slot access (getStackInSlot,
 * insertItem, extractItem, setStackInSlot, getSlotLimit, isItemValid) first asks getIndexForSlot(slot), which scans the
 * wrapper's baseIndex array from the start until it finds the first storage whose cumulative end lies above the slot:
 * k+1 steps for a slot of the k-th storage, so one pass over all slots of a 40-chest train costs ~20 steps per slot.
 *
 * The answer depends only on the slot and on baseIndex, a final array CombinedInvWrapper fills in its constructor and
 * never writes again (neither do the two wrapper classes this switch serves; other subclasses keep the scan). So the
 * answer for every slot is computed once: table[s] = the first i with s < baseIndex[i]. With m[i] = the largest of
 * baseIndex[0..i], that is the first i with m[i] > s (m[i] > s exactly when some j <= i has baseIndex[j] > s, and the
 * first such i has baseIndex[i] > s itself), so a sweep over i assigning the slots m[i-1] .. m[i]-1 to i fills the table
 * exactly, also for zero-slot storages, a non-monotone array or negative entries. Slots below 0 or at/above the largest
 * entry have no such i: the scan returns -1 for them and so does the switch. A table is built only up to MAX_SLOTS
 * entries; larger wrappers keep the scan.
 */
public final class MountedSlotIndex {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.createMountedSlotIndex=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.createMountedSlotIndex", "true"));
    /** Wrappers with more slots than this keep the scan (the table is 4 bytes per slot). */
    public static final int MAX_SLOTS = 1 << 22;
    /** Counters for probes: tables built, wrappers left on the scan because they are too large. */
    public static final AtomicLong BUILT = new AtomicLong(), TOO_LARGE = new AtomicLong();
    /** Marker for "this wrapper keeps the scan". */
    public static final int[] SCAN = new int[0];

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private MountedSlotIndex() {
    }

    /** Create's train cargo wrapper (package-private, so named here); it inherits getIndexForSlot unchanged. */
    static final String CARGO = "com.simibubi.create.content.contraptions.minecart.TrainCargoManager$CargoInvWrapper";
    static final String WRAPPER = "com.simibubi.create.api.contraption.storage.item.MountedItemStorageWrapper";

    /**
     * The table for one wrapper: SCAN for any class other than Create's MountedItemStorageWrapper and its train cargo
     * subclass (another subclass could write baseIndex), else build(baseIndex).
     */
    public static int[] tableFor(Object wrapper, int[] baseIndex) {
        String c = wrapper.getClass().getName();
        if (!c.equals(WRAPPER) && !c.equals(CARGO)) return SCAN;
        return build(baseIndex);
    }

    /** The slot -> storage index table for a baseIndex array, or SCAN when it would be larger than MAX_SLOTS. */
    public static int[] build(int[] baseIndex) {
        int max = 0;
        for (int b : baseIndex) if (b > max) max = b;
        if (max > MAX_SLOTS) {
            TOO_LARGE.incrementAndGet();
            return SCAN;
        }
        int[] table = new int[max];
        int filled = 0;
        for (int i = 0; i < baseIndex.length; i++) {
            int end = baseIndex[i];
            for (int s = filled; s < end; s++) table[s] = i;
            if (end > filled) filled = end;
        }
        BUILT.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: create_mounted_slot_index finds contraption storage slots through a table built once per storage wrapper");
        }
        return table;
    }

    /** getIndexForSlot from a table built by build(baseIndex): the scan's answer for every int slot. */
    public static int lookup(int[] table, int slot) {
        return slot >= 0 && slot < table.length ? table[slot] : -1;
    }
}
