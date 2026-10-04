package bons.furious.patch.create_logistics;

import bons.furious.mixin.create_logistics.FilteringBehaviourAccessor;
import com.google.common.base.Predicates;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch create_single_pass_extraction (Create 6.0.8, MIT, both sides).
 *
 * InvManipulationBehaviour.extract(mode, amount, filter), when not simulating, first runs the whole extraction loop
 * simulated (S), returns its result if empty, and otherwise runs the loop again for real (R). A funnel taking items out of
 * a chest therefore scans it three times per transfer: its own simulate() call, then S, then R.
 *
 * R alone gives the same answer. If S changes nothing, R starts from the same state with or without S before it, so it
 * makes the same calls and extractions and returns the same stack. If S came back empty, R also comes back with
 * ItemStack.EMPTY and has extracted nothing: up to its first real extraction R makes exactly S's calls in S's order, and
 * ItemHelper.extract extracts for real only from a matching slot (UPTO: the first one; EXACTLY: on the pass after enough
 * were found), where S would have found that item too and could not have come back empty. Both points need S's calls
 * to be pure, so the switch only acts where every call S makes is audited:
 *  - the inventory is one PureHandlers admits (simulated reads write nothing; a chest's loot table is filled by the
 *    first read of a pass, which with or without S is the first effect of the call);
 *  - the block entity is a FunnelBlockEntity (Create's funnel, both kinds), the custom filter is Guava's
 *    Predicates.alwaysTrue() (FunnelBlockEntity.activateExtractor), and the filtering behaviour is Create's own class
 *    with a plain item filter (FilterItemStack itself: an empty filter accepts everything, otherwise FilterItem.testDirect
 *    compares items); its isActive supplier is the funnel's supportsFiltering, a block-state read.
 * Everything else, and the simulated path, runs Create's method unchanged.
 */
public final class SinglePassExtraction {
    /** Runtime switch. -Dbons_and_furious.createSinglePassExtraction=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.createSinglePassExtraction", "true"));
    /** Probe counters (plain, approximate): non-simulated calls answered with one pass, calls left to Create's method. */
    public static long singlePass, original;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private SinglePassExtraction() {
    }

    /** The non-simulated call may skip the simulated pass (see the class comment). */
    public static boolean eligible(SmartBlockEntity be, Predicate<ItemStack> customFilter, IItemHandler inventory) {
        boolean ok = be != null && be.getClass() == FunnelBlockEntity.class
                && customFilter == (Object) Predicates.alwaysTrue()
                && plainFilter(be.getBehaviour(FilteringBehaviour.TYPE))
                && PureHandlers.audited(inventory);
        if (!ok) {
            original++;
            return false;
        }
        singlePass++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: create_single_pass_extraction lets funnels extract from audited inventories without the extra simulated pass");
        }
        return true;
    }

    private static boolean plainFilter(Object behaviour) {
        if (behaviour == null) return true;
        if (behaviour.getClass() != FilteringBehaviour.class || !(behaviour instanceof FilteringBehaviourAccessor a)) return false;
        FilterItemStack f = a.bons$filter();
        return f != null && f.getClass() == FilterItemStack.class;
    }
}
