package bons.furious.mixin.easyvillagers_coo49;

import bons.furious.patch.easyvillagers_coo49.DirtyMarks;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import de.maxhenkel.easyvillagers.blocks.tileentity.BreederTileentity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * easyvillagers_dirty_mark_interval (Easy Villagers 1.21.1-1.1.49 for NeoForge, server; change, off by default; no Easy
 * Villagers code carried). BreederTileentity.tickServer calls setChanged() every tick while either villager is inside; with
 * the switch on that call runs only on the block's mark ticks (DirtyMarks).
 *
 * Ported to 1.21.1: tickServer keeps its single setChanged() after both villagers' advanceAge.
 */
@Mixin(value = BreederTileentity.class, remap = false)
public abstract class BreederDirtyMarkMixin {
    @WrapWithCondition(method = "tickServer()V", at = @At(value = "INVOKE",
            target = "Lde/maxhenkel/easyvillagers/blocks/tileentity/BreederTileentity;setChanged()V"), require = 1, allow = 1)
    private boolean bons$easyvillagersBreederMark(BreederTileentity self) {
        return DirtyMarks.mark(self);
    }
}
