package bons.furious.mixin.vanilla_inventory_index;

import bons.furious.patch.vanilla_inventory_index.ListenerIndex;
import java.util.Set;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_inventory_trigger_index (Minecraft 1.20.1 on Forge 47.4.16; server side): read access to ItemPredicate's tag
 * (f_45029_) and item set (f_151427_), for ListenerIndex's simple-listener test. Accessors only.
 */
@Mixin(value = ItemPredicate.class, remap = false)
public interface ItemPredicateFieldsAccessor extends ListenerIndex.ItemFields {
    @Override
    @Accessor("f_45029_")
    TagKey<Item> bons$inventoryIndexTag();

    @Override
    @Accessor("f_151427_")
    Set<Item> bons$inventoryIndexItems();
}
