package bons.furious.patch.corpse_coo49;

import de.maxhenkel.corpse.corelib.death.Death;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch corpse_idle_scan_streamless (since 1.0.49; Corpse 1.21.1-1.1.22; both sides, acts where the
 * corpse ticks: the logical server). Helper of CorpseIdleScanMixin; no Corpse code is carried.
 *
 * What Corpse does. CorpseEntity.tick asks isEmpty() on every server tick of every corpse, and isEmpty() is
 * isMainInventoryEmpty() && isAdditionalInventoryEmpty(): four stream().allMatch(ItemStack::isEmpty) pipelines over the
 * death's main inventory, armour, off hand (in that order, each only when the one before held only empty stacks) and the
 * additional items. A corpse that was emptied walks every list on every tick, through a new stream each time.
 *
 * What the switch does. The two methods answer from plain loops over the same lists, in the same order, calling the same
 * ItemStack.isEmpty() on the same stacks and stopping at the same first non-empty stack: allMatch over a sequential
 * stream of a list is exactly "no element fails the test, tested from the first in list order, stopping at the first
 * failure". isEmpty() reads only, so the answer and every other effect are identical; only the stream objects go.
 * -Dbons_and_furious.corpseIdleScanStreamless=false runs the original methods.
 */
public final class CorpseScans {
    /** corpse_idle_scan_streamless: -Dbons_and_furious.corpseIdleScanStreamless=false runs the original streams. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.corpseIdleScanStreamless", "true"));
    private static volatile boolean announced;

    private CorpseScans() {
    }

    /** CorpseEntity.isMainInventoryEmpty: main inventory, then armour, then off hand, each only while all were empty. */
    public static boolean mainEmpty(Death death) {
        if (!announced) announce();
        return allEmpty(death.getMainInventory()) && allEmpty(death.getArmorInventory()) && allEmpty(death.getOffHandInventory());
    }

    /** CorpseEntity.isAdditionalInventoryEmpty. */
    public static boolean additionalEmpty(Death death) {
        if (!announced) announce();
        return allEmpty(death.getAdditionalItems());
    }

    /** list.stream().allMatch(ItemStack::isEmpty) without the stream: first element first, stops at the first false. */
    static boolean allEmpty(NonNullList<ItemStack> list) {
        for (int i = 0, n = list.size(); i < n; i++) {
            if (!list.get(i).isEmpty()) return false;
        }
        return true;
    }

    private static void announce() {
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: corpse_idle_scan_streamless applies (corpses check their stored items with plain loops instead of streams)");
    }
}
