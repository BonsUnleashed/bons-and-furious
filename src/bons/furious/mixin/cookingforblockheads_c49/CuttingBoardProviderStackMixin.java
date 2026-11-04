package bons.furious.mixin.cookingforblockheads_c49;

import bons.furious.patch.cookingforblockheads_c49.KitchenIndex;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cookingforblockheads_recipe_book_item_index: read access to the cutting board provider's board stack, for the kitchen
 * index. Accessor only.
 */
@Mixin(targets = "net.blay09.mods.cookingforblockheads.tile.CuttingBoardBlockEntity$1", remap = false)
public interface CuttingBoardProviderStackMixin extends KitchenIndex.CuttingBoardStack {
    @Accessor("cuttingBoard")
    ItemStack bons$cookingforblockheads_c49Board();
}
