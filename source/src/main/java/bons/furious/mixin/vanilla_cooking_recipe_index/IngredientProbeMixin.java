package bons.furious.mixin.vanilla_cooking_recipe_index;

import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_cooking_recipe_item_index (Minecraft 1.21.1 with NeoForge 21.1.252; both sides; since 1.0.49): an empty probe mixin.
 * Priority 3000, so Mixin applies it after every other mod's mixin on Ingredient; RecipeIndexPlugin.postApply then checks
 * that test(ItemStack) is still as shipped. Changes nothing.
 */
@Mixin(value = Ingredient.class, priority = 3000, remap = false)
public abstract class IngredientProbeMixin {
}
