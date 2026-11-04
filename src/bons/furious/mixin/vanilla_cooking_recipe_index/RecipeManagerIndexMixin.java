package bons.furious.mixin.vanilla_cooking_recipe_index;

import bons.furious.patch.vanilla_cooking_recipe_index.CookingRecipeIndex;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_cooking_recipe_item_index (Minecraft 1.20.1 on Forge 47.4.28; both sides; since 1.0.49): gives every RecipeManager
 * one field, the layer's memo (CookingRecipeIndex.Holder), and is the layer's probe on this class: priority 3000, so Mixin
 * applies it after every other mod's mixin and RecipeIndexPlugin.postApply sees getRecipeFor, its filter lambda and byType as
 * they will run. No method of RecipeManager is changed. No Minecraft code is carried.
 */
@Mixin(value = RecipeManager.class, priority = 3000, remap = false)
public abstract class RecipeManagerIndexMixin implements CookingRecipeIndex.Holder {
    @Unique
    private volatile Object bons$cookingIndex;

    @Override
    public Object bons$cookingIndex() {
        return this.bons$cookingIndex;
    }

    @Override
    public void bons$cookingIndex(Object index) {
        this.bons$cookingIndex = index;
    }
}