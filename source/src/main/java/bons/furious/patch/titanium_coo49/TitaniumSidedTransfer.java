package bons.furious.patch.titanium_coo49;

import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch titanium_sided_transfer_empty_skip (since 1.0.49; Titanium 4.0.52 for NeoForge 1.21.1, server side
 * in effect; helper of SidedTransferEmptyMixin; no Titanium code is carried).
 *
 * What Titanium does. SidedInventoryComponent.transfer (every automatic push or pull of a sided machine inventory) first
 * simulates taking from the source slot, then scans every slot of the destination for one that would take that stack
 * (isValidForAnySlot: getSlots, isItemValid, getStackInSlot and a simulated insertItem for every slot that accepts the
 * item, a chest = 27 or 54 slots, modded storage more), and only then checks whether the simulated stack was empty; for an
 * empty one the scan's answer is not used: the slot cache moves on and the method returns false.
 *
 * What the switch does (exact). When the simulated stack is empty that first scan is not run (its answer would be thrown
 * away; for an empty stack it is -1 anyway, since no simulated insert can leave fewer than zero items). The scan only asks
 * the destination handler's query methods and simulated inserts, which NeoForge's IItemHandler contract defines as
 * changing nothing, so nothing else changes: same return value, same slot cache, same inventories, same insert and extract
 * calls. The re-scan after each insert inside the transfer loop (the second isValidForAnySlot call) runs as shipped.
 * Runtime flag bons_and_furious.titaniumSidedTransferEmptySkip.
 */
public final class TitaniumSidedTransfer {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.titaniumSidedTransferEmptySkip", "true"));
    /** Statistics for probes and the harness (plain field): scans left out. */
    public static long skipped;
    private static volatile boolean announced;

    private TitaniumSidedTransfer() {
    }

    /** One destination scan was left out for an empty simulated stack. */
    public static void skipped() {
        skipped++;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: titanium_sided_transfer_empty_skip applies (a sided push or pull from an empty slot no longer scans the destination)");
        }
    }
}
