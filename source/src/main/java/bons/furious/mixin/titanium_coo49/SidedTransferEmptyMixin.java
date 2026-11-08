package bons.furious.mixin.titanium_coo49;

import bons.furious.patch.titanium_coo49.TitaniumSidedTransfer;
import com.hrznstudio.titanium.component.inventory.SidedInventoryComponent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * titanium_sided_transfer_empty_skip (Titanium 4.0.52 for NeoForge 1.21.1, server side in effect; exact; no Titanium code
 * is carried). In SidedInventoryComponent.transfer the first destination scan isValidForAnySlot (ordinal 0, the one made
 * right after the simulated extract) is not run for an empty simulated stack, whose scan answer the method never uses. See
 * TitaniumSidedTransfer.
 */
@Mixin(value = SidedInventoryComponent.class, remap = false)
public abstract class SidedTransferEmptyMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "transfer(Lcom/hrznstudio/titanium/util/FacingUtil$Sideness;Lnet/neoforged/neoforge/items/IItemHandler;Lnet/neoforged/neoforge/items/IItemHandler;I)Z",
            at = @At(value = "INVOKE", target = "Lcom/hrznstudio/titanium/component/inventory/SidedInventoryComponent;isValidForAnySlot(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;)I", ordinal = 0),
            require = 1, allow = 1)
    private int bons$titaniumSidedTransferEmpty(SidedInventoryComponent self, IItemHandler dest, ItemStack stack, Operation<Integer> original) {
        if (TitaniumSidedTransfer.enabled && stack.isEmpty()) {
            TitaniumSidedTransfer.skipped();
            return -1;
        }
        return original.call(self, dest, stack);
    }
}
