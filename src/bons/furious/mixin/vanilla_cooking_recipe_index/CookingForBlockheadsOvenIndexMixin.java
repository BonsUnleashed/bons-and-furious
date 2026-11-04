package bons.furious.mixin.vanilla_cooking_recipe_index;

import bons.furious.patch.vanilla_cooking_recipe_index.CookingRecipeIndex;
import bons.furious.patch.vanilla_cooking_recipe_index.IndexCallers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cookingforblockheads_oven_result_index (Cooking for Blockheads 16.0.15; both sides; since 1.0.49), on the
 * vanilla_cooking_recipe_item_index layer.
 *
 * OvenBlockEntity.getSmeltingResult asks level.getRecipeManager().getRecipeFor(SMELTING, its one-slot wrapper, level) for every
 * oven slot shown on the screen each frame, every hopper insert test and every finished item, and only unwraps the answer
 * (orElse(null)). That one call is wrapped: the layer answers the same recipe from its first-match index when its probe and
 * token allow, otherwise the original call is made. Our own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "net.blay09.mods.cookingforblockheads.tile.OvenBlockEntity", remap = false)
public abstract class CookingForBlockheadsOvenIndexMixin {
    @WrapOperation(method = "getSmeltingResult(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;m_44015_(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"),
            require = 1, allow = 1, remap = false)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<?> bons$ovenRecipe(RecipeManager manager, RecipeType<?> type, Container container, Level level, Operation<Optional<?>> original) {
        return CookingRecipeIndex.recipeFor(IndexCallers.COOKING_FOR_BLOCKHEADS, manager, type, container, level, (Operation) original);
    }
}