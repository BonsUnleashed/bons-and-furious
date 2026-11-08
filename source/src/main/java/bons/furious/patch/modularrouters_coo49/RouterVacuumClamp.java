package bons.furious.patch.modularrouters_coo49;

import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious 1.0.49 switch modularrouters_vacuum_negative_split_fix (Modular Routers 13.2.7+mc1.21.1, server; kind
 * change (gameplay fix), off by default (opt-in under the speed rule); helper of VacuumClampMixin; no Modular Routers code
 * carried).
 *
 * The bug. CompiledVacuumModule.handleItemMode works out the room left in the router's buffer as
 * min(maxStackSize, regulation amount) - items already buffered when a regulator augment is fitted. Once the buffer holds
 * more than the regulator amount (the amount was lowered, or another module filled the buffer) that room is negative, and
 * stackOnGround.split(min(itemsPerTick, room)) is ItemStack.split of a negative count: it returns an empty (negative-count)
 * stack and GROWS the item on the ground by the same amount. Inserting the empty stack inserts nothing (ItemStackHandler
 * returns ItemStack.EMPTY for an empty stack), so nothing gives the items back: every run of the module duplicates items on
 * the ground.
 *
 * The fix. The split amount is clamped at 0: split(0) takes nothing, the insert inserts nothing, no particles, nothing is
 * counted as picked up. Every other run (room 0 or more) is untouched. Runtime flag
 * bons_and_furious.modularroutersVacuumNegativeSplitFix.
 */
public final class RouterVacuumClamp {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modularroutersVacuumNegativeSplitFix", "true"));
    private static volatile boolean announced;

    private RouterVacuumClamp() {
    }

    /** The split amount handleItemMode asks for, clamped at 0 while the switch is on. */
    public static int amount(int requested) {
        if (requested >= 0 || !enabled) return requested;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: modularrouters_vacuum_negative_split_fix applies (a vacuum module whose buffer already holds more than its regulator amount takes nothing instead of duplicating the item on the ground)");
        }
        return 0;
    }
}
