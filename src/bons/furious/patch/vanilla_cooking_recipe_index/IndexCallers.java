package bons.furious.patch.vanilla_cooking_recipe_index;

/**
 * Bons and Furious 1.0.49: the callers of the vanilla_cooking_recipe_item_index layer in this group, each its own switch with its
 * own guard (its call site), so one updated mod steps aside alone. See CookingRecipeIndex.
 */
public final class IndexCallers {
    public static final CookingRecipeIndex.Caller COOKING_FOR_BLOCKHEADS = CookingRecipeIndex.caller("cookingforblockheads_oven_result_index",
            "cookingforblockheadsOvenResultIndex", "Cooking for Blockheads' oven finds its smelting recipe from the index");
    public static final CookingRecipeIndex.Caller MYSTICAL_AGRICULTURE = CookingRecipeIndex.caller("mysticalagriculture_essence_furnace_recipe_index",
            "mysticalagricultureEssenceFurnaceRecipeIndex", "Mystical Agriculture's essence furnaces find their smelting recipe from the index");

    private IndexCallers() {
    }
}