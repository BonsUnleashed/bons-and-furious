package bons.furious.mixin.vanilla_inventory_index;

import bons.furious.patch.vanilla_inventory_index.ListenerIndex;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_inventory_trigger_index (Minecraft 1.20.1; server side): read access to InventoryChangeTrigger.TriggerInstance's
 * item conditions (f_43179_), for ListenerIndex's simple-listener test. Accessor only.
 */
@Mixin(value = InventoryChangeTrigger.TriggerInstance.class, remap = false)
public interface TriggerInstancePredicatesAccessor extends ListenerIndex.Predicates {
    @Override
    @Accessor("f_43179_")
    ItemPredicate[] bons$inventoryIndexPredicates();
}
