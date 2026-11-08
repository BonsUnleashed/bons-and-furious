package bons.furious.mixin.mysticalagriculture_coo49;

import bons.furious.patch.mysticalagriculture_coo49.SoulExtractorEmpty;
import com.blakebr0.cucumber.inventory.CachedRecipe;
import com.blakebr0.mysticalagriculture.tileentity.SoulExtractorTileEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mysticalagriculture_soul_extractor_empty_skip (Mystical Agriculture with Cucumber, MIT; 1.21.1 tested build: Mystical
 * Agriculture 1.21.1-8.0.29 with Cucumber 1.21.1-8.0.16, server; kind change, OFF by default; no Mystical Agriculture or
 * Cucumber code carried). SoulExtractorTileEntity.getActiveRecipe(): with an empty input slot the remembered recipe is
 * forgotten and null answered without the look-up. Trade-off: see SoulExtractorEmpty.
 *
 * Ported to 1.21.1: getActiveRecipe passes Cucumber's CachedRecipe.checkAndGet the extractor's three slots as one
 * ShapelessCraftingInput (toCraftingInput); slot 0 of that input is the input slot's own stack.
 */
@Mixin(value = SoulExtractorTileEntity.class, remap = false)
public abstract class SoulExtractorEmptyMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "getActiveRecipe()Lcom/blakebr0/mysticalagriculture/api/crafting/ISoulExtractionRecipe;",
            at = @At(value = "INVOKE", target = "Lcom/blakebr0/cucumber/inventory/CachedRecipe;checkAndGet(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/item/crafting/Recipe;"),
            require = 1, allow = 1)
    private Recipe bons$mysticalagricultureSoulEmpty(CachedRecipe cached, RecipeInput input, Level level, Operation<Recipe> original) {
        if (SoulExtractorEmpty.enabled && SoulExtractorEmpty.empty(input)) {
            ((CachedRecipeAccessor) cached).bons$mysticalagricultureCachedRecipe(null);
            return null;
        }
        return original.call(cached, input, level);
    }
}
