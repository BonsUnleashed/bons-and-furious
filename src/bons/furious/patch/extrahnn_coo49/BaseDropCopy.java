package bons.furious.patch.extrahnn_coo49;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch extrahnn_ultimate_chamber_base_drop_copy (Extra Hostile Neural Networks 1.2.2 with Hostile Neural
 * Networks 5.3.3, server; kind change, a gameplay fix that is off by default). Extra Hostile Neural Networks is MIT; none of
 * its code is carried. Ours, idea from Collections Of Optimizations (extrahnn.fixSharedBaseDrop), idea text only.
 *
 * What Extra Hostile Neural Networks does: when a run of the Ultimate Simulation Chamber ends, setResult asks every stored
 * model for DataModel.baseDrop() and calls setCount(multiplier) on the stack it gets. DataModel is a record and baseDrop()
 * hands out the loaded model's own stack, so the count lands on Hostile Neural Networks' data: from then on, until the next
 * datapack reload, every Simulation Chamber, recipe-viewer page and tooltip shows and produces the chamber's multiplier as
 * that model's base drop count, and the next Ultimate chamber's free-space check reads it too. The chamber's own output
 * is placed by setItem, which copies the stacks into its slots (the model's stack never ends up in a slot in 1.2.2).
 *
 * Gameplay fix (with the switch on): setResult works on a copy of the base drop (the call's result is copied), so the
 * count is written to the copy: the chamber produces exactly the same items, and the data model keeps its own count. One
 * consequence: the chamber's free-space check (canStartSimulation, unchanged) then sees the model's own base drop count, as
 * it does in the shipped mod for prediction drops and for the first run after every reload.
 *
 * Flag: -Dbons_and_furious.extrahnnUltimateChamberBaseDropCopy=false writes into the model's stack again.
 */
public final class BaseDropCopy {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.extrahnnUltimateChamberBaseDropCopy", "true"));
    /** Base drops copied (for probes and the harness). */
    public static final AtomicLong COPIED = new AtomicLong();
    private static volatile boolean announced;

    private BaseDropCopy() {
    }

    /** setResult's baseDrop() result: a copy while the switch is on. */
    public static ItemStack base(ItemStack modelStack) {
        if (!enabled) return modelStack;
        COPIED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: extrahnn_ultimate_chamber_base_drop_copy applies (the Ultimate Simulation Chamber no longer writes into the data models' base drops)");
        }
        return modelStack.m_41777_();
    }
}
