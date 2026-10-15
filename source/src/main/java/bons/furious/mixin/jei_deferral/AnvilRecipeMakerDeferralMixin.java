package bons.furious.mixin.jei_deferral;

import bons.furious.patch.jei_deferral.Deferral;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * jei_join_recipe_deferral (JEI 19.51.0.418): AnvilRecipeMaker.getAnvilRecipes() = concat(repair recipes, enchanted-book
 * recipes).toList(). For JEI's own vanilla-plugin call this returns the repair recipes as before and hands JEI's own
 * enchanted-book stream to {@link Deferral}, which adds those recipes after JEI has started. The stream walks a snapshot of
 * the ingredient list taken here (JEI's getAllItemStacks() is a live view), so it visits exactly the items the original call
 * would have. Every other caller gets JEI's method as shipped.
 */
@Mixin(value = AnvilRecipeMaker.class, remap = false)
public abstract class AnvilRecipeMakerDeferralMixin {
    @Shadow(remap = false)
    private Stream<IJeiAnvilRecipe> getRepairRecipes() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private Stream<IJeiAnvilRecipe> getBookEnchantmentRecipes() {
        throw new AssertionError();
    }

    @Inject(method = "getAnvilRecipes()Ljava/util/List;", at = @At("HEAD"), cancellable = true, remap = false)
    private void bons$deferBookRecipes(CallbackInfoReturnable<List<IJeiAnvilRecipe>> cir) {
        if (!Deferral.takeAnvilArm()) {
            return;
        }
        List<IJeiAnvilRecipe> repairRecipes = this.getRepairRecipes().toList();
        Stream<IJeiAnvilRecipe> bookRecipes;
        Deferral.snapshotting(true);
        try {
            bookRecipes = this.getBookEnchantmentRecipes();
        } finally {
            Deferral.snapshotting(false);
        }
        Deferral.scheduleAnvil(bookRecipes, repairRecipes.size());
        cir.setReturnValue(repairRecipes);
    }

    @Redirect(method = "getBookEnchantmentRecipes()Ljava/util/stream/Stream;",
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/runtime/IIngredientManager;getAllItemStacks()Ljava/util/Collection;", remap = false),
            remap = false)
    private Collection<ItemStack> bons$snapshotBookIngredients(IIngredientManager ingredientManager) {
        Collection<ItemStack> all = ingredientManager.getAllItemStacks();
        return Deferral.snapshotting() ? List.copyOf(all) : all;
    }

    /**
     * JEI builds the enchanted-book recipes as ingredients.flatMap(ingredient -> recipes of that ingredient). Pulled through
     * an iterator, flatMap computes ALL of one ingredient's recipes at once (a modded sword with dozens of enchantments = one
     * ~10 ms step). For the deferral the same mapper is walked one recipe at a time instead: same ingredients, same mapper,
     * same order, finer steps for the per-frame budget.
     */
    @Redirect(method = "getBookEnchantmentRecipes()Ljava/util/stream/Stream;",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;flatMap(Ljava/util/function/Function;)Ljava/util/stream/Stream;", remap = false),
            remap = false)
    private Stream<IJeiAnvilRecipe> bons$oneBookRecipeAtATime(Stream<ItemStack> ingredients,
                                                               Function<ItemStack, Stream<IJeiAnvilRecipe>> mapper) {
        return Deferral.snapshotting() ? Deferral.oneAtATime(ingredients.iterator(), mapper) : ingredients.flatMap(mapper);
    }
}
