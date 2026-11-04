package bons.furious.mixin.easyvillagers_coo49;

import bons.furious.patch.easyvillagers_coo49.DirtyMarks;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import de.maxhenkel.easyvillagers.blocks.tileentity.FarmerTileentity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * easyvillagers_dirty_mark_interval (Easy Villagers 1.20.1-1.1.39, server; change, off by default; no Easy Villagers code
 * carried). FarmerTileentity.tickServer calls setChanged() every tick while a villager is inside (its first call; the
 * second one, after a crop aged, is left alone); with the switch on that call runs only on the block's mark ticks.
 */
@Mixin(value = FarmerTileentity.class, remap = false)
public abstract class FarmerDirtyMarkMixin {
    @WrapWithCondition(method = "tickServer()V", at = @At(value = "INVOKE",
            target = "Lde/maxhenkel/easyvillagers/blocks/tileentity/FarmerTileentity;m_6596_()V", ordinal = 0), require = 1, allow = 1)
    private boolean bons$easyvillagersFarmerMark(FarmerTileentity self) {
        return DirtyMarks.mark(self);
    }
}
