package bons.furious.mixin.cookingforblockheads_c49;

import bons.furious.patch.cookingforblockheads_c49.KitchenIndex;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cookingforblockheads_recipe_book_item_index: read access to the milk jar provider's fixed list of milk items (cow jars use
 * the same provider), for the kitchen index. Accessor only.
 */
@Mixin(targets = "net.blay09.mods.cookingforblockheads.tile.MilkJarBlockEntity$MilkJarItemProvider", remap = false)
public interface MilkJarProviderStacksMixin extends KitchenIndex.FixedStacks {
    @Accessor("itemStacks")
    NonNullList<ItemStack> bons$cookingforblockheads_c49Stacks();
}
