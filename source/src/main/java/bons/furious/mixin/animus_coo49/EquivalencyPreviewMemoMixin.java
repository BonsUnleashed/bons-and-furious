package bons.furious.mixin.animus_coo49;

import bons.furious.patch.animus_coo49.EquivalencyPreviews;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;

/**
 * animus_equivalency_preview_memo (Animus: NeoVitae 5.2.25 for Minecraft 1.21.1, mod id animusnv, client side; MIT, no Animus
 * code is carried). The Sigil of Equivalency's preview search (SigilEquivalencyRenderer.findMatchingBlocksInRadius, private
 * static, run every frame while the sigil is aimed at a block) re-reads the positions its last search polled, in the same
 * order; while every outcome is the same it returns the same matches without rebuilding the search, otherwise it continues
 * from the first change. Same reads, same result. See EquivalencyPreviews. On 1.21.1 the renderer is
 * com.breakinblocks.animusnv.client.SigilEquivalencyRenderer with the same search body as Animus 3.0.35's.
 */
@Mixin(targets = "com.breakinblocks.animusnv.client.SigilEquivalencyRenderer", remap = false)
public abstract class EquivalencyPreviewMemoMixin {
    @WrapMethod(method = "findMatchingBlocksInRadius(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;ILnet/minecraft/core/Direction;)Ljava/util/List;")
    private static List<BlockPos> bons$animusEquivalencyPreview(Level level, BlockPos center, Block target, int radius, Direction face, Operation<List<BlockPos>> original) {
        return EquivalencyPreviews.find(level, center, target, radius, face, original);
    }
}
