package bons.furious.mixin.mysticalagriculture_coo49;

import bons.furious.patch.mysticalagriculture_coo49.SoulExtractorEmpty;
import com.blakebr0.cucumber.inventory.BaseItemStackHandler;
import com.blakebr0.cucumber.inventory.CachedRecipe;
import com.blakebr0.mysticalagriculture.tileentity.SoulExtractorTileEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mysticalagriculture_soul_extractor_empty_skip (Mystical Agriculture 7.0.24 with Cucumber 7.0.16, server; kind change, OFF
 * by default; no Mystical Agriculture or Cucumber code carried). SoulExtractorTileEntity.getActiveRecipe(): with an empty
 * input slot the remembered recipe is forgotten and null answered without the look-up. Trade-off: see SoulExtractorEmpty.
 */
@Mixin(value = SoulExtractorTileEntity.class, remap = false)
public abstract class SoulExtractorEmptyMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "getActiveRecipe()Lcom/blakebr0/mysticalagriculture/api/crafting/ISoulExtractionRecipe;",
            at = @At(value = "INVOKE", target = "Lcom/blakebr0/cucumber/inventory/CachedRecipe;checkAndGet(Lcom/blakebr0/cucumber/inventory/BaseItemStackHandler;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/item/crafting/Recipe;"),
            require = 1, allow = 1)
    private Recipe bons$mysticalagricultureSoulEmpty(CachedRecipe cached, BaseItemStackHandler inventory, Level level, Operation<Recipe> original) {
        if (SoulExtractorEmpty.enabled && SoulExtractorEmpty.empty(inventory)) {
            ((CachedRecipeAccessor) cached).bons$mysticalagricultureCachedRecipe(null);
            return null;
        }
        return original.call(cached, inventory, level);
    }
}
