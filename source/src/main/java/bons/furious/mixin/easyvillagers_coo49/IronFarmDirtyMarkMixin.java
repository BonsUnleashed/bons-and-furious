package bons.furious.mixin.easyvillagers_coo49;

import bons.furious.patch.easyvillagers_coo49.DirtyMarks;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import de.maxhenkel.easyvillagers.blocks.tileentity.IronFarmTileentity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * easyvillagers_dirty_mark_interval (Easy Villagers 1.21.1-1.1.49 for NeoForge, both sides: the iron farm ticks on both;
 * change, off by default; no Easy Villagers code carried). IronFarmTileentity.tick calls setChanged() every tick while a
 * villager is inside (after counting its timer); with the switch on that call runs only on the block's mark ticks
 * (DirtyMarks).
 *
 * Ported to 1.21.1: tick (ITickableBlockEntity) keeps its single setChanged() after ++timer.
 */
@Mixin(value = IronFarmTileentity.class, remap = false)
public abstract class IronFarmDirtyMarkMixin {
    @WrapWithCondition(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lde/maxhenkel/easyvillagers/blocks/tileentity/IronFarmTileentity;setChanged()V"), require = 1, allow = 1)
    private boolean bons$easyvillagersIronFarmMark(IronFarmTileentity self) {
        return DirtyMarks.mark(self);
    }
}
