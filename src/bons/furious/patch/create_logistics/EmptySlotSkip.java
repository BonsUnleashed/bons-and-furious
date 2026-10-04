package bons.furious.patch.create_logistics;

import com.simibubi.create.foundation.item.ItemHelper;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch create_item_helper_empty_slots (Create 6.0.8, MIT, both sides).
 *
 * ItemHelper.extract(inv, test, mode, amount, simulate) is the extraction loop behind funnels (through
 * InvManipulationBehaviour), chutes, contraption funnels, deployers, droppers, the schematicannon and Clockwork's delivery
 * cannon. For every slot of every pass it asks the slot's stack for its max stack size and then simulates an extraction
 * from the slot, and only then looks at whether anything came out: an empty slot costs a getStackInSlot, a
 * getMaxStackSize and a full simulated extractItem. Funnels and chutes on chests and barrels poll like this every tick
 * while nothing can be taken (only Create's versioned inventories stop the polling), so most of that work is empty slots.
 *
 * extract below is Create's method (MIT) with one change: the slot's stack is fetched by the same getStackInSlot call at
 * the same point, and when it is empty the slot is skipped right there. For the handlers PureHandlers admits, an empty
 * getStackInSlot means the simulated extractItem returns ItemStack.EMPTY without any effect, and an empty extracted stack
 * makes Create's loop go to the next slot without calling test (the || short-circuits), so the skipped calls are reads whose
 * outcome is known; everything else (passes, restarts, the black-list predicate, real extractions, the result object) is
 * Create's own sequence. Any other handler runs Create's original method.
 */
public final class EmptySlotSkip {
    /** Runtime switch. -Dbons_and_furious.createItemHelperEmptySlots=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.createItemHelperEmptySlots", "true"));
    /** Probe counters (plain, approximate under threads): calls on the fast loop, calls left to Create's method, empty slots skipped. */
    public static long fastCalls, originalCalls, skippedSlots;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private EmptySlotSkip() {
    }

    /** True when extract may run the loop below for this handler. */
    public static boolean applies(IItemHandler inv) {
        boolean ok = PureHandlers.audited(inv);
        if (!ok) originalCalls++;
        return ok;
    }

    /**
     * Create 6.0.8's ItemHelper.extract(IItemHandler, Predicate, ExtractionCountMode, int, boolean) (MIT), statement for
     * statement, except that an empty slot is skipped before getMaxStackSize / extractItem (see the class comment).
     */
    public static ItemStack extract(IItemHandler inv, Predicate<ItemStack> test, ItemHelper.ExtractionCountMode mode, int amount, boolean simulate) {
        fastCalls++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: create_item_helper_empty_slots skips empty slots of audited inventories in Create's item extraction loop");
        }
        long skipped = 0;
        ItemStack extracting = ItemStack.f_41583_;
        boolean amountRequired = mode == ItemHelper.ExtractionCountMode.EXACTLY;
        boolean checkHasEnoughItems = amountRequired;
        boolean hasEnoughItems = !checkHasEnoughItems;
        boolean potentialOtherMatch = false;
        int maxExtractionCount = amount;
        pass:
        while (true) {
            extracting = ItemStack.f_41583_;
            for (int slot = 0; slot < inv.getSlots(); ++slot) {
                int room = maxExtractionCount - extracting.m_41613_();
                ItemStack inSlot = inv.getStackInSlot(slot);
                if (inSlot.m_41619_()) {
                    skipped++;
                    continue;
                }
                int amountToExtractFromThisSlot = Math.min(room, inSlot.m_41741_());
                ItemStack stack = inv.extractItem(slot, amountToExtractFromThisSlot, true);
                if (stack.m_41619_() || !test.test(stack)) continue;
                if (!extracting.m_41619_() && !ItemHelper.canItemStackAmountsStack(stack, extracting)) {
                    potentialOtherMatch = true;
                    continue;
                }
                if (extracting.m_41619_()) {
                    extracting = stack.m_41777_();
                } else {
                    extracting.m_41769_(stack.m_41613_());
                }
                if (!simulate && hasEnoughItems) {
                    inv.extractItem(slot, stack.m_41613_(), false);
                }
                if (extracting.m_41613_() < maxExtractionCount) continue;
                if (!checkHasEnoughItems) break pass;
                hasEnoughItems = true;
                checkHasEnoughItems = false;
                continue pass;
            }
            if (!extracting.m_41619_() && !hasEnoughItems && potentialOtherMatch) {
                ItemStack blackListed = extracting.m_41777_();
                test = test.and(i -> !ItemHandlerHelper.canItemStacksStack(i, blackListed));
                continue;
            }
            if (!checkHasEnoughItems) break;
            checkHasEnoughItems = false;
        }
        if (skipped != 0) skippedSlots += skipped;
        if (amountRequired && extracting.m_41613_() < amount) {
            return ItemStack.f_41583_;
        }
        return extracting;
    }
}
