package bons.furious.mixin.mysticalagriculture_coo49;

import java.util.Map;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * mysticalagriculture_soul_extractor_idle_skip (Mystical Agriculture 7.0.24, server; no game code carried): the recipe
 * table accessor (RecipeManager.recipes, f_44007_, the map byType reads) and, through its priority 3000 and the group's
 * MysticalAgriculturePlugin, the probe that records whether getRecipeFor, its filter and byType are still as shipped after
 * every other mod's mixins (MaProbe).
 */
@Mixin(value = RecipeManager.class, priority = 3000, remap = false)
public interface RecipeManagerProbeMixin {
    @Accessor("f_44007_")
    Map<?, ?> bons$mysticalagricultureRecipes();
}
