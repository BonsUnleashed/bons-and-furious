package bons.furious.mixin.vanilla_cooking_recipe_index;

import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_cooking_recipe_item_index (Minecraft 1.21.1 with NeoForge 21.1.252; both sides; since 1.0.49): an empty probe mixin.
 * Priority 3000, so Mixin applies it after every other mod's mixin on AbstractCookingRecipe; RecipeIndexPlugin.postApply then
 * checks that matches(SingleRecipeInput, Level) (ingredient.test(input.item())) and its bridge matches(RecipeInput, Level)
 * are still as shipped. Changes nothing.
 */
@Mixin(value = AbstractCookingRecipe.class, priority = 3000, remap = false)
public abstract class AbstractCookingRecipeProbeMixin {
}
