package bons.furious.mixin.create_logistics;

import bons.furious.patch.create_logistics.SinglePassExtraction;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * create_single_pass_extraction (Create 6.0.8, MIT, both sides; tested build 6.0.8).
 *
 * InvManipulationBehaviour.extract(mode, amount, filter): a non-simulated call that SinglePassExtraction admits (a funnel
 * with a plain or no filter on an audited inventory) runs the original's own statements without the simulated pass that
 * precedes the real one: simulateNext cleared, client check, the target inventory, getFilterTest, one real
 * ItemHelper.extract. Every other call (simulated, other block entities, other filters or inventories, switch off) runs
 * Create's method. Why the outcome is the same: SinglePassExtraction.
 */
@Mixin(value = InvManipulationBehaviour.class, remap = false)
public abstract class InvManipulationExtractMixin extends CapManipulationBehaviourBase<IItemHandler, InvManipulationBehaviour> {
    private InvManipulationExtractMixin() {
        super(null, null);
    }

    @Shadow
    protected abstract Predicate<ItemStack> getFilterTest(Predicate<ItemStack> customFilter);

    @WrapMethod(method = "extract(Lcom/simibubi/create/foundation/item/ItemHelper$ExtractionCountMode;ILjava/util/function/Predicate;)Lnet/minecraft/world/item/ItemStack;")
    private ItemStack bons$singlePass(ItemHelper.ExtractionCountMode mode, int amount, Predicate<ItemStack> filter, Operation<ItemStack> original) {
        if (!SinglePassExtraction.enabled || this.simulateNext) return original.call(mode, amount, filter);
        Level world = this.getWorld();
        if (world.f_46443_) return original.call(mode, amount, filter);   // isClientSide: the original returns EMPTY
        IItemHandler inventory = this.targetCapability.orElse(null);
        SmartBlockEntity be = this.blockEntity;
        if (inventory == null || !SinglePassExtraction.eligible(be, filter, inventory)) return original.call(mode, amount, filter);
        this.simulateNext = false;
        Predicate<ItemStack> test = this.getFilterTest(filter);
        return ItemHelper.extract(inventory, test, mode, amount, false);
    }
}
