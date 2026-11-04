package bons.furious.mixin.bloodmagic_coo49;

import bons.furious.patch.bloodmagic_coo49.BmRecipes;
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
 * bloodmagic_arc_furnace_recipe_memo (Blood Magic 3.3.8-50, CC BY 4.0; both sides; exact), a caller of the
 * vanilla_cooking_recipe_item_index layer: MixinExtras @WrapOperation on the RecipeManager.getRecipeFor (m_44015_) call of
 * TileAlchemicalReactionChamber.tick, the alchemical reaction chamber's furnace mode (a furnace tool in, no ARC recipe for the
 * input): getRecipeFor(SMELTING, a one-slot copy of the input, level) every tick, whose answer the tick only unwraps. The
 * layer answers the same recipe from its first-match index when its probe and token allow, otherwise the original call is
 * made. See BmRecipes. Our own logic only; nothing of Blood Magic is carried.
 */
@Mixin(targets = "wayoftime.bloodmagic.common.tile.TileAlchemicalReactionChamber", remap = false)
public abstract class BmArcFurnaceRecipeMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;m_44015_(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"),
            require = 1, allow = 1, remap = false)
    private Optional bons$bloodmagicCoo49FurnaceRecipe(RecipeManager manager, RecipeType type, Container container, Level level, Operation<Optional> original) {
        return BmRecipes.furnace(manager, type, container, level, original);
    }
}
