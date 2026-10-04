package bons.furious.mixin.regionsunexplored_c2;

import bons.furious.patch.regionsunexplored_c2.FuelBurnMemo;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * regionsunexplored_fuel_burn_memo (Minecraft 1.20.1 Item; both sides): one field per Item that holds Regions
 * Unexplored's recorded fuel-listener calls for that item (see {@link FuelBurnMemo}). Keeping the answer on the item gives
 * the identity semantics of the listener's == comparisons without a map lookup. Nothing else in Item changes; the field
 * starts as null (not seen yet) and is written once per item with an immutable, shared array.
 */
@Mixin(value = Item.class, remap = false)
public abstract class ItemFuelMemoSlotMixin implements FuelBurnMemo.Slot {
    @Unique
    private volatile int[] bons$ruFuel;

    @Override
    public int[] bons$ruFuelCalls() {
        return this.bons$ruFuel;
    }

    @Override
    public void bons$ruFuelCalls(int[] calls) {
        this.bons$ruFuel = calls;
    }
}
