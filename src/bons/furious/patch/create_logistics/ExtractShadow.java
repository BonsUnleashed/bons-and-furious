package bons.furious.patch.create_logistics;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shadow mode of create_item_helper_empty_slots (-Dbons_and_furious.createItemHelperEmptySlots.shadow=true), for rigs:
 * every simulated ItemHelper.extract call that took the fast loop is repeated with Create's original method and the two
 * results are compared (same emptiness, item, count and tag). A simulated call changes nothing, so running it twice is
 * harmless. At most 20 mismatches are logged.
 */
public final class ExtractShadow {
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.createItemHelperEmptySlots.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    private ExtractShadow() {
    }

    public static void compareSimulated(ItemStack ours, ItemStack theirs, Object inv) {
        SHADOW_CHECKS.incrementAndGet();
        boolean same = ours.m_41619_() == theirs.m_41619_() && ItemStack.m_41728_(ours, theirs);
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: create_item_helper_empty_slots shadow check: {} vs Create's {} for {}", ours, theirs, inv.getClass().getName());
    }
}
