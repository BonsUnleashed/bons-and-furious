package bons.furious.mixin.almostunified;

import com.almostreliable.unified.recipe.RecipeLink;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * almostunified_duplicate_groups (Almost Unified 0.11.0, both sides): access to RecipeLink.isCraftingRecipe (final) and
 * to its private getCraftingRecipeOutput(), AU's own lazily cached output item (called in the same order as AU calls it).
 * Nothing in RecipeLink changes.
 */
@Mixin(value = RecipeLink.class, remap = false)
public interface RecipeLinkAccessor {
    @Accessor(value = "isCraftingRecipe", remap = false)
    boolean bons$isCraftingRecipe();

    @Invoker(value = "getCraftingRecipeOutput", remap = false)
    Item bons$craftingRecipeOutput();
}
