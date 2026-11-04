package bons.furious.mixin.bloodmagic_coo49;

import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * bloodmagic_arc_recipe_list_memo (Minecraft 1.20.1 on Forge 47.4.28; both sides): the switch's probe on RecipeManager.
 * Priority 3000, so Mixin applies it after every other mod's mixin on the class, and BmPlugin.postApply then sees
 * getAllRecipesFor (m_44013_) and byType (m_44054_) as they will run: the list memo is used only while both are as shipped.
 * No method of RecipeManager is changed; the one @Unique method only keeps this mixin from being empty. No Minecraft code is
 * carried.
 */
@Mixin(value = RecipeManager.class, priority = 3000, remap = false)
public abstract class BmRecipeManagerProbeMixin {
    @Unique
    private static boolean bons$bloodmagicCoo49Probe() {
        return true;
    }
}
