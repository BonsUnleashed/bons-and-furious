package bons.furious.mixin.titanium_coo49;

import bons.furious.patch.titanium_coo49.TitaniumRecipeLists;
import com.hrznstudio.titanium.util.RecipeUtil;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * titanium_recipe_list_snapshot (Titanium 4.0.52 for NeoForge 1.21.1, both sides; exact; no Titanium code is carried).
 * RecipeUtil.getRecipes answers a new list copied from a kept list of the type's recipes while the recipe manager's table
 * is the same immutable table; any other table, and a null answer of the helper, runs Titanium's own method. See
 * TitaniumRecipeLists.
 */
@Mixin(value = RecipeUtil.class, remap = false)
public abstract class RecipeUtilSnapshotMixin {
    @SuppressWarnings("rawtypes")
    @Inject(method = "getRecipes(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$titaniumRecipeListSnapshot(Level world, RecipeType<?> type, CallbackInfoReturnable<List> cir) {
        if (!TitaniumRecipeLists.enabled || !TitaniumRecipeLists.READY) return;
        List kept = TitaniumRecipeLists.recipes(world, type);
        if (kept != null) cir.setReturnValue(kept);
    }
}
