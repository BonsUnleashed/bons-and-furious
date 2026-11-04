package bons.furious.mixin.cookingforblockheads_c49;

import bons.furious.patch.cookingforblockheads_c49.KitchenIndex;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cookingforblockheads_recipe_book_item_index: read access to the sink provider's fixed list of water items, for the
 * kitchen index. Accessor only.
 */
@Mixin(targets = "net.blay09.mods.cookingforblockheads.tile.SinkBlockEntity$SinkItemProvider", remap = false)
public interface SinkProviderStacksMixin extends KitchenIndex.FixedStacks {
    @Accessor("itemStacks")
    NonNullList<ItemStack> bons$cookingforblockheads_c49Stacks();
}
