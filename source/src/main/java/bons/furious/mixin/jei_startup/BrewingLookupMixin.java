package bons.furious.mixin.jei_startup;

import bons.furious.patch.jei_startup.BrewingLookup;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.library.util.BrewingRecipeMakerCommon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BrewingRecipeMakerCommon.class, remap = false)
public abstract class BrewingLookupMixin {
    @WrapOperation(method = "getVanillaBrewingRecipes", at = @At(value = "NEW", target = "java/util/HashSet"))
    private static HashSet<IJeiBrewingRecipe> bons$ownedIndex(Operation<HashSet<IJeiBrewingRecipe>> original) {
        return BrewingLookup.enabled ? new BrewingLookup.Recipes() : original.call();
    }

    @WrapOperation(method = "getNewPotions", at = @At(value = "INVOKE", target = "Ljava/util/Collection;stream()Ljava/util/stream/Stream;"))
    private static Stream<IJeiBrewingRecipe> bons$candidates(Collection<IJeiBrewingRecipe> recipes,
            Operation<Stream<IJeiBrewingRecipe>> original, @Local(ordinal = 0) IJeiBrewingRecipe query) {
        if (recipes instanceof BrewingLookup.Recipes indexed) {
            Stream<IJeiBrewingRecipe> candidates = indexed.candidates(query);
            if (candidates != null) return candidates;
        }
        return original.call(recipes);
    }
}
