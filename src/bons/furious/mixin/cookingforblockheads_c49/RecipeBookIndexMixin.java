package bons.furious.mixin.cookingforblockheads_c49;

import bons.furious.patch.cookingforblockheads_c49.KitchenIndex;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.blay09.mods.cookingforblockheads.KitchenMultiBlock;
import net.blay09.mods.cookingforblockheads.api.capability.IKitchenItemProvider;
import net.blay09.mods.cookingforblockheads.menu.RecipeBookMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cookingforblockheads_recipe_book_item_index (Cooking for Blockheads 16.0.15): in RecipeBookMenu.findAndSendItemList and
 * findAndSendRecipes the provider list CookingRegistry.getItemProviders builds is indexed once (KitchenIndex.open), and the
 * index is dropped when the method ends, also when it throws (KitchenIndex explains why the answers are the same).
 * Injectors only (All Rights Reserved target).
 */
@Mixin(value = RecipeBookMenu.class, remap = false)
public abstract class RecipeBookIndexMixin {
    @WrapOperation(method = {"findAndSendItemList()V", "findAndSendRecipes(Lnet/minecraft/world/item/ItemStack;Z)V"}, at = @At(value = "INVOKE",
            target = "Lnet/blay09/mods/cookingforblockheads/registry/CookingRegistry;getItemProviders(Lnet/blay09/mods/cookingforblockheads/KitchenMultiBlock;Lnet/minecraft/world/entity/player/Inventory;)Ljava/util/List;"),
            require = 2, allow = 2)
    private List<IKitchenItemProvider> bons$indexedProviders(KitchenMultiBlock multiBlock, Inventory inventory, Operation<List<IKitchenItemProvider>> original) {
        return KitchenIndex.open(original.call(multiBlock, inventory));
    }

    @WrapMethod(method = "findAndSendItemList()V")
    private void bons$itemListScope(Operation<Void> original) {
        try {
            original.call();
        } finally {
            KitchenIndex.close();
        }
    }

    @WrapMethod(method = "findAndSendRecipes(Lnet/minecraft/world/item/ItemStack;Z)V")
    private void bons$recipesScope(ItemStack outputItem, boolean noFilterPreview, Operation<Void> original) {
        try {
            original.call(outputItem, noFilterPreview);
        } finally {
            KitchenIndex.close();
        }
    }
}
