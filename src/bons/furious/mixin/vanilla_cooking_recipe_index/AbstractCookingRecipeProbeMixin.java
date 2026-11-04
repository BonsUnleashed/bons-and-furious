package bons.furious.mixin.vanilla_cooking_recipe_index;

import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_cooking_recipe_item_index (since 1.0.49): an empty probe mixin. Priority 3000, so Mixin applies it after every other
 * mod's mixin on AbstractCookingRecipe; RecipeIndexPlugin.postApply then checks that matches (m_5818_) is still as shipped
 * (ingredient.test(container.getItem(0))). Changes nothing.
 */
@Mixin(value = AbstractCookingRecipe.class, priority = 3000, remap = false)
public abstract class AbstractCookingRecipeProbeMixin {
}