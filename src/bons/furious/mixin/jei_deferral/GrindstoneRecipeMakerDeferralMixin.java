package bons.furious.mixin.jei_deferral;

import bons.furious.guard.Guards;
import bons.furious.patch.jei_deferral.Deferral;
import bons.furious.patch.jei_deferral.GrindstoneDisenchant;
import bons.furious.patch.jei_deferral.GrindstoneDisenchantGroups;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.IPlatformRecipeHelper;
import mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * jei_join_recipe_deferral (JEI 15.71.0.224 tested, JEI 15.59.0.212 accepted): GrindstoneRecipeMaker.getGrindstoneRecipes(
 * manager, helper, menu) = concat(repair recipes, disenchant recipes).toList(), every recipe a simulated grindstone, and the
 * disenchant half an eager loop over every enchantment x every registered item. For JEI's own vanilla-plugin call this
 * registers nothing now and hands {@link Deferral} JEI's own (lazy) repair stream plus a step-by-step walk of the
 * disenchant loop that calls JEI's own private canEnchant (and, on 15.59, getGrindstoneRecipe; on 15.62+, the platform
 * helper's getGrindstoneResult, as JEI's grouped loop does): {@link GrindstoneDisenchant} for 15.59's loop (one recipe per
 * item, enchantment and level), {@link GrindstoneDisenchantGroups} for 15.62's (one recipe per enchantment and level). Which
 * loop is installed is the build label the switch's guard matched (Guards.matchedBuild). The deferral adds them, repair
 * first, after JEI has started. Every other caller gets JEI's method as shipped.
 */
@Mixin(value = GrindstoneRecipeMaker.class, remap = false)
public abstract class GrindstoneRecipeMakerDeferralMixin {
    @Shadow(remap = false)
    private static Stream<IJeiGrindstoneRecipe> getRepairRecipes(IPlatformRecipeHelper platformHelper, IIngredientManager ingredientManager,
                                                                GrindstoneMenu grindstoneMenu) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private static boolean canEnchant(IPlatformRecipeHelper platformHelper, ItemStack stack, Enchantment enchantment, ResourceLocation enchantmentId) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private static IJeiGrindstoneRecipe getGrindstoneRecipe(IPlatformRecipeHelper platformHelper, GrindstoneMenu grindstoneMenu,
                                                           ItemStack topInput, ItemStack bottomInput, ResourceLocation uid) {
        throw new AssertionError();
    }

    @Inject(method = "getGrindstoneRecipes(Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/common/platform/IPlatformRecipeHelper;"
            + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/List;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bons$deferGrindstoneRecipes(IIngredientManager ingredientManager, IPlatformRecipeHelper platformHelper,
                                                    GrindstoneMenu grindstoneMenu, CallbackInfoReturnable<List<IJeiGrindstoneRecipe>> cir) {
        if (!Deferral.takeGrindstoneArm()) {
            return;
        }
        Stream<IJeiGrindstoneRecipe> repairRecipes;
        Deferral.snapshotting(true);
        try {
            repairRecipes = getRepairRecipes(platformHelper, ingredientManager, grindstoneMenu);
        } finally {
            Deferral.snapshotting(false);
        }
        if (Deferral.JEI_1559.equals(Guards.matchedBuild("jei_join_recipe_deferral"))) {
            // JEI 15.59: one recipe per item, enchantment and level
            GrindstoneDisenchant disenchant = new GrindstoneDisenchant(
                    (stack, enchantment, enchantmentId) -> canEnchant(platformHelper, stack, enchantment, enchantmentId),
                    (topInput, bottomInput, uid) -> getGrindstoneRecipe(platformHelper, grindstoneMenu, topInput, bottomInput, uid));
            Deferral.scheduleGrindstone(repairRecipes.iterator(), disenchant);
        } else {
            // JEI 15.62 and later (tested 15.71): one recipe per enchantment and level holding every supported item
            GrindstoneDisenchantGroups disenchant = new GrindstoneDisenchantGroups(
                    (stack, enchantment, enchantmentId) -> canEnchant(platformHelper, stack, enchantment, enchantmentId),
                    (topInput, bottomInput) -> platformHelper.getGrindstoneResult(grindstoneMenu, topInput, bottomInput));
            Deferral.scheduleGrindstone(repairRecipes.iterator(), disenchant);
        }
        cir.setReturnValue(List.of());
    }

    @Redirect(method = "getRepairRecipes",
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/runtime/IIngredientManager;getAllItemStacks()Ljava/util/Collection;", remap = false),
            remap = false)
    private static Collection<ItemStack> bons$snapshotRepairIngredients(IIngredientManager ingredientManager) {
        Collection<ItemStack> all = ingredientManager.getAllItemStacks();
        return Deferral.snapshotting() ? List.copyOf(all) : all;
    }
}
