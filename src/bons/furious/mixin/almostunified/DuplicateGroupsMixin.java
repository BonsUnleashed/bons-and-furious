package bons.furious.mixin.almostunified;

import bons.furious.patch.almostunified.DuplicateGroups;
import com.almostreliable.unified.config.DuplicationConfig;
import com.almostreliable.unified.recipe.RecipeLink;
import com.almostreliable.unified.recipe.RecipeTransformer;
import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * almostunified_duplicate_groups (Almost Unified 0.11.0, both sides).
 *
 * RecipeTransformer.handleDuplicate(cur, recipes) compares one unified recipe with every recipe of its type. It now
 * compares it only with the recipes that can be its duplicates (same compared fields, equal values, same output for
 * crafting types), in list order, through Almost Unified's own RecipeLink.handleDuplicate; every other comparison of the
 * original returns false without side effects (DuplicateGroups explains why, including the order in which crafting
 * outputs are first computed). The ignore checks and the compare context are AU's own calls, in the original order.
 * With the runtime switch off, or for a list AU never builds (null entry, mixed types), the original method runs.
 * Almost Unified is LGPL-3.0; the loop is AU's own with the candidate list replaced (helper DuplicateGroups).
 */
@Mixin(value = RecipeTransformer.class, remap = false)
public abstract class DuplicateGroupsMixin {
    @Shadow
    @Final
    private DuplicationConfig duplicationConfig;

    @WrapMethod(method = "handleDuplicate(Lcom/almostreliable/unified/recipe/RecipeLink;Ljava/util/List;)Z")
    private boolean bons$groupedDuplicates(RecipeLink curRecipe, List<RecipeLink> recipes, Operation<Boolean> original) {
        if (!DuplicateGroups.enabled) return original.call(curRecipe, recipes);
        return DuplicateGroups.handle(this.duplicationConfig, curRecipe, recipes, original);
    }

    /** The transform is over: drop the last list's index (it holds that list's recipes). */
    @Inject(method = "transformRecipes(Ljava/util/Map;Z)Lcom/almostreliable/unified/recipe/RecipeTransformer$Result;", at = @At("RETURN"))
    private void bons$releaseGroups(Map<ResourceLocation, JsonElement> recipes, boolean skipClientTracking, CallbackInfoReturnable<RecipeTransformer.Result> cir) {
        DuplicateGroups.release();
    }
}
