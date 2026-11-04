package bons.furious.mixin.mysticalagriculture_coo49;

import com.blakebr0.mysticalagriculture.crafting.recipe.SoulExtractionRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mysticalagriculture_soul_extractor_idle_skip (Mystical Agriculture 7.0.24, server; no Mystical Agriculture code carried):
 * a probe only. With priority 3000 it is applied after every other mod's mixin on SoulExtractionRecipe, and the group's
 * MysticalAgriculturePlugin then records whether its two matches methods are still as shipped (MaProbe). It changes
 * nothing in the class.
 */
@Mixin(value = SoulExtractionRecipe.class, priority = 3000, remap = false)
public abstract class SoulRecipeProbeMixin {
    @Unique
    private static void bons$mysticalagricultureSoulRecipeProbe() {
    }
}
