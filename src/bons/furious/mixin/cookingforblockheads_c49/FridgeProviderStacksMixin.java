package bons.furious.mixin.cookingforblockheads_c49;

import bons.furious.patch.cookingforblockheads_c49.KitchenIndex;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cookingforblockheads_recipe_book_item_index: read access to the fridge provider's snow and ice stacks (the items its ice
 * unit offers), for the kitchen index. Accessors only.
 */
@Mixin(targets = "net.blay09.mods.cookingforblockheads.tile.FridgeBlockEntity$2", remap = false)
public interface FridgeProviderStacksMixin extends KitchenIndex.FridgeStacks {
    @Accessor("snowStack")
    ItemStack bons$cookingforblockheads_c49Snow();

    @Accessor("iceStack")
    ItemStack bons$cookingforblockheads_c49Ice();
}
