package bons.furious.mixin.cookingforblockheads_c49;

import bons.furious.patch.cookingforblockheads_c49.KitchenIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.blay09.mods.cookingforblockheads.api.SourceItem;
import net.blay09.mods.cookingforblockheads.api.capability.IKitchenItemProvider;
import net.blay09.mods.cookingforblockheads.registry.CookingRegistry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cookingforblockheads_recipe_book_item_index (Cooking for Blockheads 16.0.15): the findAnyItemStack call in
 * CookingRegistry.findSourceCandidates answers null at once while the open kitchen index says no provider holds the asked
 * item (KitchenIndex explains why that is the answer the search gives). Injector only (All Rights Reserved target).
 */
@Mixin(value = CookingRegistry.class, remap = false)
public abstract class SourceSearchIndexMixin {
    @WrapOperation(method = "findSourceCandidates", at = @At(value = "INVOKE",
            target = "Lnet/blay09/mods/cookingforblockheads/registry/CookingRegistry;findAnyItemStack(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Z)Lnet/blay09/mods/cookingforblockheads/api/SourceItem;"),
            require = 1, allow = 1)
    private static SourceItem bons$indexedSearch(ItemStack stack, List<IKitchenItemProvider> providers, boolean requireBucket, Operation<SourceItem> original) {
        if (!KitchenIndex.absent(stack, providers)) return original.call(stack, providers, requireBucket);
        if (KitchenIndex.SHADOW) {
            SourceItem found = original.call(stack, providers, requireBucket);
            KitchenIndex.SHADOW_CHECKS.incrementAndGet();
            if (found != null) KitchenIndex.shadowFound(stack);
            return found;
        }
        KitchenIndex.answered++;
        return null;
    }
}
