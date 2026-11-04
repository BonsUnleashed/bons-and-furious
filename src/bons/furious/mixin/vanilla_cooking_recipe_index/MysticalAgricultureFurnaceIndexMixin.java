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
 * mysticalagriculture_essence_furnace_recipe_index (Mystical Agriculture 7.0.24; both sides; since 1.0.49), on the
 * vanilla_cooking_recipe_item_index layer.
 *
 * EssenceFurnaceTileEntity.tick asks level.getRecipeManager().getRecipeFor(SMELTING, the furnace, level) on every tick the
 * furnace burns or holds fuel and input, and only unwraps the answer (orElse(null)). That one call is wrapped: the layer
 * answers the same recipe from its first-match index when its probe and token allow, otherwise the original call is made. Our
 * own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "com.blakebr0.mysticalagriculture.tileentity.EssenceFurnaceTileEntity", remap = false)
public abstract class MysticalAgricultureFurnaceIndexMixin {
    @WrapOperation(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/blakebr0/mysticalagriculture/tileentity/EssenceFurnaceTileEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;m_44015_(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"),
            require = 1, allow = 1, remap = false)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<?> bons$furnaceRecipe(RecipeManager manager, RecipeType<?> type, Container container, Level level, Operation<Optional<?>> original) {
        return CookingRecipeIndex.recipeFor(IndexCallers.MYSTICAL_AGRICULTURE, manager, type, container, level, (Operation) original);
    }
}