package bons.furious.mixin.bloodmagic_coo49;

import bons.furious.patch.bloodmagic_coo49.BmRecipes;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * bloodmagic_arc_recipe_list_memo (Blood Magic 3.3.8-50, CC BY 4.0; server; exact): @Redirect of the
 * RecipeManager.getAllRecipesFor (m_44013_) call of BloodMagicRecipeRegistrar.getARC, which every alchemical reaction
 * chamber with an input and a tool makes every tick: List.copyOf of every ARC recipe, a list getARC only iterates. On a
 * server level (getARC's own level argument, captured) the list built for the current recipe table is handed out again
 * while that table is unchanged; everywhere else the same call is made. A redirect (not a MixinExtras wrap) so the paths
 * that run as shipped pay a few field tests and no operation object or argument array. See BmRecipes. Our own logic only.
 */
@Mixin(targets = "wayoftime.bloodmagic.impl.BloodMagicRecipeRegistrar", remap = false)
public abstract class BmArcRecipeListMixin {
    @SuppressWarnings({"rawtypes"})
    @Redirect(method = "getARC(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraftforge/fluids/FluidStack;)Lwayoftime/bloodmagic/recipe/RecipeARC;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;m_44013_(Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/List;"),
            require = 1, allow = 1, remap = false)
    private List bons$bloodmagicCoo49ArcList(RecipeManager manager, RecipeType type, Level world, ItemStack input, ItemStack tool, FluidStack fluid) {
        return BmRecipes.arcList(manager, type, world);
    }
}
