package bons.furious.mixin.create_logistics;

import bons.furious.patch.create_logistics.EmptySlotSkip;
import bons.furious.patch.create_logistics.ExtractShadow;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.foundation.item.ItemHelper;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;

/**
 * create_item_helper_empty_slots (Create 6.0.8, MIT, both sides; tested build 6.0.8).
 *
 * ItemHelper.extract(IItemHandler, Predicate, ExtractionCountMode, int, boolean) runs EmptySlotSkip.extract (Create's own
 * loop, skipping empty slots before the simulated extractItem) when the handler's read path is one PureHandlers audited;
 * for any other handler, or with the runtime switch off, Create's method runs unchanged. Why the result, the inventory
 * and every remaining call are the same: EmptySlotSkip. Shadow mode (-Dbons_and_furious.createItemHelperEmptySlots.shadow
 * =true) also runs Create's method for every simulated call and compares the results.
 */
@Mixin(value = ItemHelper.class, remap = false)
public abstract class ItemHelperExtractMixin {
    @WrapMethod(method = "extract(Lnet/minecraftforge/items/IItemHandler;Ljava/util/function/Predicate;Lcom/simibubi/create/foundation/item/ItemHelper$ExtractionCountMode;IZ)Lnet/minecraft/world/item/ItemStack;")
    private static ItemStack bons$emptySlots(IItemHandler inv, Predicate<ItemStack> test, ItemHelper.ExtractionCountMode mode, int amount, boolean simulate,
                                             Operation<ItemStack> original) {
        if (!EmptySlotSkip.enabled || !EmptySlotSkip.applies(inv)) return original.call(inv, test, mode, amount, simulate);
        ItemStack result = EmptySlotSkip.extract(inv, test, mode, amount, simulate);
        if (ExtractShadow.SHADOW && simulate) ExtractShadow.compareSimulated(result, original.call(inv, test, mode, amount, true), inv);
        return result;
    }
}
