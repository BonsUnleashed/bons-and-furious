package bons.furious.mixin.create_logistics;

import bons.furious.patch.create_logistics.MountedSlotIndex;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageWrapper;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * create_mounted_slot_index (Create 6.0.8, MIT, both sides; tested build 6.0.8 with Forge 47.4.16).
 *
 * Adds getIndexForSlot to MountedItemStorageWrapper (which inherits it from Forge's CombinedInvWrapper, LGPL-2.1): the
 * same answer, read from a table built once from the wrapper's final baseIndex array instead of scanning that array on
 * every slot access. TrainCargoManager$CargoInvWrapper inherits the method. Why the table answers exactly as the scan
 * does for every int, and which wrappers keep the scan: MountedSlotIndex. With the runtime switch off, or for any other
 * subclass, Forge's scan runs.
 */
@Mixin(value = MountedItemStorageWrapper.class, remap = false)
public abstract class MountedSlotIndexMixin extends CombinedInvWrapper {
    @Unique
    private volatile int[] bons$slotTable;

    private MountedSlotIndexMixin() {
        super();
    }

    @Override
    protected int getIndexForSlot(int slot) {
        if (!MountedSlotIndex.enabled) return super.getIndexForSlot(slot);
        int[] table = this.bons$slotTable;
        if (table == null) this.bons$slotTable = table = MountedSlotIndex.tableFor(this, this.baseIndex);
        if (table == MountedSlotIndex.SCAN) return super.getIndexForSlot(slot);
        return MountedSlotIndex.lookup(table, slot);
    }
}
