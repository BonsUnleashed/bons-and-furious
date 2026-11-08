package bons.furious.mixin.mysticalagriculture_coo49;

import com.google.common.collect.Multimap;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * mysticalagriculture_soul_extractor_idle_skip (Mystical Agriculture 1.21.1-8.0.29, server; no game code carried): the recipe
 * table accessor (Minecraft 1.21.1 RecipeManager.byType, the immutable multimap the private byType(RecipeType) reads; apply
 * and replaceRecipes put a new one) and, through its priority 3000 and the group's MysticalAgriculturePlugin, the probe that
 * records whether both getRecipeFor overloads of the look-up, its filter and byType are still as shipped after every other
 * mod's mixins (MaProbe).
 */
@Mixin(value = RecipeManager.class, priority = 3000, remap = false)
public interface RecipeManagerProbeMixin {
    @Accessor("byType")
    Multimap<?, ?> bons$mysticalagricultureByType();
}
