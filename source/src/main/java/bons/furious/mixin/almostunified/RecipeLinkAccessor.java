package bons.furious.mixin.almostunified;

import com.almostreliable.unified.unification.recipe.RecipeLink;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * almostunified_duplicate_groups (Almost Unified 1.4.2 for NeoForge 1.21.1, both sides): read access to
 * RecipeLink.isCraftingRecipe (private final, no getter). Nothing in RecipeLink changes.
 *
 * Ported to 1.21.1: RecipeLink moved to com.almostreliable.unified.unification.recipe; its getCraftingRecipeOutput()
 * (AU's own lazily cached output item) is public in 1.4.2, so the 1.20.1 invoker for it is gone and DuplicateGroups calls
 * it directly, in the same order as AU calls it.
 */
@Mixin(value = RecipeLink.class, remap = false)
public interface RecipeLinkAccessor {
    @Accessor(value = "isCraftingRecipe", remap = false)
    boolean bons$isCraftingRecipe();
}
