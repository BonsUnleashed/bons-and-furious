package bons.furious.mixin.mysticalagriculture_coo49;

import com.blakebr0.cucumber.inventory.CachedRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * mysticalagriculture_soul_extractor_empty_skip (Cucumber 7.0.16 CachedRecipe; no Cucumber code carried): the remembered
 * recipe field, set to null as Cucumber's own look-up does when it finds nothing.
 */
@Mixin(value = CachedRecipe.class, remap = false)
public interface CachedRecipeAccessor {
    @Accessor("recipe")
    void bons$mysticalagricultureCachedRecipe(Recipe<?> recipe);
}
