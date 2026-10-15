package bons.furious.mixin.jei_deferral;

import bons.furious.patch.jei_deferral.Deferral;
import java.util.List;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.recipes.RecipeManagerInternal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * jei_join_recipe_deferral (JEI 15.59.0.212): the single path for adding JEI recipes (registration-time
 * IRecipeRegistration.addRecipes and the run-time IRecipeManager.addRecipes both end here). While deferred recipes of a type
 * are pending, later additions of that type are held so they land after those recipes, as in JEI's original order. With
 * nothing pending (the switch never armed, or every lane is done) the handler returns at once.
 */
@Mixin(value = RecipeManagerInternal.class, remap = false)
public abstract class RecipeManagerInternalDeferralMixin {
    @Inject(method = "addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void bons$holdLaterRecipes(RecipeType<?> recipeType, List<?> recipes, CallbackInfo ci) {
        if (Deferral.beforeAdd((RecipeManagerInternal) (Object) this, recipeType, recipes)) {
            ci.cancel();
        }
    }
}
