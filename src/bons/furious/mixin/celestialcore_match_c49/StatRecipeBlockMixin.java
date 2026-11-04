package bons.furious.mixin.celestialcore_match_c49;

import bons.furious.patch.celestialcore_match_c49.RecipeBlockMatch;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * celestialcore_transformation_recipe_match (Celestial Core 1.5.x), part 3 of 3: TransformationRecipe.matches (always true
 * as shipped) answers false inside the item handler's lookup when the recipe's "stat" block is neither the block at the
 * item's getOnPos() nor the block below it (RecipeBlockMatch.blockUnder); everywhere else as shipped. Our own logic only
 * (LGPL-2.1 target; none of its code is carried). @Pseudo with a string target: the class does not exist in 1.6.x (the mixin then has no target and Mixin skips it without a warning).
 */
@Pseudo
@Mixin(targets = "com.xiaoyue.celestial_core.content.recipes.TransformationRecipe", remap = false)
public abstract class StatRecipeBlockMixin {
    @Shadow
    public Block stat;

    @ModifyReturnValue(method = "matches(Lcom/xiaoyue/celestial_core/content/recipes/TransformationRecipe$Inv;Lnet/minecraft/world/level/Level;)Z",
            at = @At("RETURN"))
    private boolean bons$celestialcore_match_c49Stat(boolean matched) {
        return matched && RecipeBlockMatch.blockUnder(this.stat, true);
    }
}
