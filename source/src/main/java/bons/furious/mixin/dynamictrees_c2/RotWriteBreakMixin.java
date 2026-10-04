package bons.furious.mixin.dynamictrees_c2;

import bons.furious.patch.dynamictrees_c2.RotCycleGuard;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dynamictrees_rot_cycle_guard (Dynamic Trees, MIT; 1.21.1 tested build: dynamictrees-neoforge-1.21.1-1.7.2; both sides):
 * BranchBlock.breakDeliberate, the removal behind every rot. Its removeBlock / setBlock result (which the original discards)
 * is counted when it reports success, so RotCycleGuard knows whether the world can have changed since a rot call began.
 * The value is passed through unchanged. MIT target, no Dynamic Trees code carried.
 * Ported to 1.21.1: same two LevelAccessor writes in 1.7.2 (the fluid read before them is ChunkTreeHelper.getStateSafe).
 */
@Mixin(value = BranchBlock.class, remap = false)
public abstract class RotWriteBreakMixin {
    @ModifyExpressionValue(method = "breakDeliberate", require = 2, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")})
    private boolean bons$rotWrite(boolean success) {
        return RotCycleGuard.wrote(success);
    }
}
