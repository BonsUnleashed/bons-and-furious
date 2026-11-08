package bons.furious.mixin.vanilla_cooking_recipe_index;

import bons.furious.patch.vanilla_cooking_recipe_index.CfbOvenIndexCaller;
import bons.furious.patch.vanilla_cooking_recipe_index.CookingRecipeIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cookingforblockheads_oven_result_index (Cooking for Blockheads 21.1.24 for Minecraft 1.21.1; both sides; since 1.0.49), on
 * the vanilla_cooking_recipe_item_index layer.
 *
 * OvenBlockEntity.getSmeltingResult(ItemStack) asks, for every oven slot shown on the screen, every hopper insert test and
 * every finished item, four recipe types in turn (the mod's own oven recipes, campfire cooking, smoking, smelting) through
 * getSmeltingResult(RecipeType, RecipeInput), which makes one server.getRecipeManager().getRecipeFor(type, its one-slot input,
 * level) and only unwraps the answer (orElse(null), then the holder's recipe assembles the result). That one call is
 * wrapped: the layer answers the same recipe holder from its first-match index when its probe and token allow, otherwise
 * the original call is made. Our own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity", remap = false)
public abstract class CookingForBlockheadsOvenIndexMixin {
    @WrapOperation(method = "getSmeltingResult(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"),
            require = 1, allow = 1, remap = false)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<?> bons$ovenRecipe(RecipeManager manager, RecipeType<?> type, RecipeInput input, Level level, Operation<Optional<?>> original) {
        return CookingRecipeIndex.recipeFor(CfbOvenIndexCaller.CALLER, manager, type, input, level, (Operation) original);
    }
}
