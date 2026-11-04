package bons.furious.patch.arsadditions_coo49;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group arsadditions_coo49 (Ars Additions 1.20.1-1.6.7): arsadditions_charm_held_only_inventory_tick
 * (gameplay fix, off by default). Helper of AaCharmHeldMixin; Ars Additions is LGPL-3.0, no code of it is carried.
 *
 * <p>Every charm runs its whole charm routine from Item.inventoryTick, which the game calls for every inventory slot, and
 * the routine then looks for the charm of its type in the main hand, the off hand and the curio slots only: a charm in the
 * inventory can never be the one found, so its tick either does nothing or spends a charge of the charm that IS held or
 * equipped (three spare night vision charms make the equipped one drain four times as fast). With the switch a charm's
 * inventory tick runs only for the stack in the main hand or the off hand; charms in curio slots keep their curio tick.
 *
 * <p>Runtime flag (=false: off): bons_and_furious.arsadditionsCharmHeldOnlyInventoryTick.
 */
public final class AaCharms {
    static final String KEY = "arsadditions_charm_held_only_inventory_tick";
    public static volatile boolean heldOnly = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.arsadditionsCharmHeldOnlyInventoryTick", "true"));
    /** Statistics (plain field): inventory ticks of charms that were not in a hand. */
    public static long skipped;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private AaCharms() {
    }

    /** HEAD of Charm.inventoryTick: true = the tick of a charm that is in neither hand is skipped. */
    public static boolean skipStowed(ItemStack stack, Entity entity) {
        if (!heldOnly || !(entity instanceof LivingEntity living)) return false;
        if (stack == living.m_21205_() || stack == living.m_21206_()) return false;
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {}: charms tick from the inventory only while held", KEY);
        }
        return true;
    }
}
