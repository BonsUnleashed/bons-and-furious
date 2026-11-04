package bons.furious.mixin.easyvillagers_coo49;

import bons.furious.patch.easyvillagers_coo49.DirtyMarks;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import de.maxhenkel.easyvillagers.blocks.tileentity.TraderTileentityBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * easyvillagers_dirty_mark_interval (Easy Villagers 1.20.1-1.1.39, server; change, off by default; no Easy Villagers code
 * carried). TraderTileentityBase.tickServer (trader and auto trader) calls setChanged() every tick while a villager is
 * inside; with the switch on that one call runs only on the block's mark ticks (DirtyMarks: every N ticks, own offset).
 */
@Mixin(value = TraderTileentityBase.class, remap = false)
public abstract class TraderDirtyMarkMixin {
    @WrapWithCondition(method = "tickServer()V", at = @At(value = "INVOKE",
            target = "Lde/maxhenkel/easyvillagers/blocks/tileentity/TraderTileentityBase;m_6596_()V"), require = 1, allow = 1)
    private boolean bons$easyvillagersTraderMark(TraderTileentityBase self) {
        return DirtyMarks.mark(self);
    }
}
