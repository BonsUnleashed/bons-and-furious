package bons.furious.mixin.titanium_coo49;

import bons.furious.patch.titanium_coo49.TitaniumSidedTransfer;
import com.hrznstudio.titanium.component.inventory.SidedInventoryComponent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * titanium_sided_transfer_empty_skip (Titanium 3.8.35, server side in effect; exact; no Titanium code is carried).
 * In SidedInventoryComponent.transfer the destination scan isValidForAnySlot is not run for an empty simulated stack, whose
 * scan answer the method never uses, unless the destination is a vanilla container that still has its loot to roll (the scan's
 * first read rolls it). See TitaniumSidedTransfer.
 */
@Mixin(value = SidedInventoryComponent.class, remap = false)
public abstract class SidedTransferEmptyMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "transfer(Lcom/hrznstudio/titanium/util/FacingUtil$Sideness;Lnet/minecraftforge/items/IItemHandler;Lnet/minecraftforge/items/IItemHandler;I)Z",
            at = @At(value = "INVOKE", target = "Lcom/hrznstudio/titanium/component/inventory/SidedInventoryComponent;isValidForAnySlot(Lnet/minecraftforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;)I"),
            require = 1, allow = 1)
    private int bons$titaniumSidedTransferEmpty(SidedInventoryComponent self, IItemHandler dest, ItemStack stack, Operation<Integer> original) {
        if (TitaniumSidedTransfer.enabled && stack.m_41619_() && !TitaniumSidedTransfer.fillsOnRead(dest)) {
            TitaniumSidedTransfer.skipped();
            return -1;
        }
        return original.call(self, dest, stack);
    }
}
