package bons.furious.patch.vanilla_cooking_recipe_index;

/**
 * Bons and Furious 1.0.49: the Cooking for Blockheads caller of the vanilla_cooking_recipe_item_index layer, its own switch
 * (cookingforblockheads_oven_result_index) with its own guard (its call site), so an updated Cooking for Blockheads steps
 * aside alone. See CookingRecipeIndex and CookingForBlockheadsOvenIndexMixin.
 */
public final class CfbOvenIndexCaller {
    public static final CookingRecipeIndex.Caller CALLER = CookingRecipeIndex.caller("cookingforblockheads_oven_result_index",
            "cookingforblockheadsOvenResultIndex", "Cooking for Blockheads' oven finds its cooking recipes from the index");

    private CfbOvenIndexCaller() {
    }
}
