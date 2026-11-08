package bons.furious.mixin.mysticalagriculture_coo49;

import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mysticalagriculture_soul_extractor_idle_skip (Mystical Agriculture 1.21.1-8.0.29, server; no game code carried): a probe
 * only. With priority 3000 it is applied after every other mod's mixin on Ingredient, and the group's
 * MysticalAgriculturePlugin then records whether Ingredient.test, getItems and isEmpty are still as shipped (MaProbe). It
 * changes nothing in the class.
 */
@Mixin(value = Ingredient.class, priority = 3000, remap = false)
public abstract class IngredientProbeMixin {
    @Unique
    private static void bons$mysticalagricultureIngredientProbe() {
    }
}
