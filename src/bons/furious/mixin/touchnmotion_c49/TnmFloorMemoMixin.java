package bons.furious.mixin.touchnmotion_c49;

import bons.furious.patch.touchnmotion_c49.TnmFrame;
import bons.furious.patch.touchnmotion_c49.TnmMemo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * touchnmotion_pose_block_memo (Touch'n Motion 1.0.4, client, since 1.0.49): after each floor probe, TerrainBalance.floor
 * reads the hit block's state again and, for a fence, wall or gate, its outline shape. @Redirect of that
 * getBlockState (the state the probe's walk read this frame) and on the outline getShape (made once per block and frame).
 * See TnmMemo. No Touch'n Motion code is carried (GPL-3.0).
 */
@Mixin(targets = "strm.touchnmotion.footgrounding.compat.TerrainBalance", remap = false)   // package-private class
public abstract class TnmFloorMemoMixin {
    @Redirect(method = "floor(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/phys/Vec3;Ljava/util/List;)Lstrm/touchnmotion/footgrounding/compat/TerrainBalance$Floor;",
            require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState bons$touchnmotionC49FloorState(Level level, BlockPos pos) {
        return TnmFrame.open ? TnmMemo.state(level, pos) : level.m_8055_(pos);
    }

    @Redirect(method = "floor(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/phys/Vec3;Ljava/util/List;)Lstrm/touchnmotion/footgrounding/compat/TerrainBalance$Floor;",
            require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;m_60808_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private static VoxelShape bons$touchnmotionC49FloorOutline(BlockState state, BlockGetter getter, BlockPos pos) {
        return TnmFrame.open ? TnmMemo.outline(state, getter, pos) : state.m_60808_(getter, pos);
    }
}
