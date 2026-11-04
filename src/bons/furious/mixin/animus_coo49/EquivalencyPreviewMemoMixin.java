package bons.furious.mixin.animus_coo49;

import bons.furious.patch.animus_coo49.EquivalencyPreviews;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teamdman.animus.client.SigilEquivalencyRenderer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;

/**
 * animus_equivalency_preview_memo (Animus 3.0.35, client side; MIT, no Animus code is carried). The Sigil of Equivalency's
 * preview search (SigilEquivalencyRenderer.findMatchingBlocksInRadius, run every frame while the sigil is aimed at a block)
 * re-reads the positions its last search polled, in the same order; while every outcome is the same it returns the same
 * matches without rebuilding the search, otherwise it continues from the first change. Same reads, same result. See
 * EquivalencyPreviews.
 */
@Mixin(value = SigilEquivalencyRenderer.class, remap = false)
public abstract class EquivalencyPreviewMemoMixin {
    @WrapMethod(method = "findMatchingBlocksInRadius(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;ILnet/minecraft/core/Direction;)Ljava/util/List;")
    private static List<BlockPos> bons$animusEquivalencyPreview(Level level, BlockPos center, Block target, int radius, Direction face, Operation<List<BlockPos>> original) {
        return EquivalencyPreviews.find(level, center, target, radius, face, original);
    }
}
