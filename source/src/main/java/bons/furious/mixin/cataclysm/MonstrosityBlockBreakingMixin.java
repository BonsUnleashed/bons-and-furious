package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.BlockBreakReads;
import com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.NewNetherite_Monstrosity.Netherite_Monstrosity_Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_boss_block_breaking (Netherite_Monstrosity_Entity; L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is
 * carried; 1.21.1 tested build: L_Ender's Cataclysm 1.21.1-3.33; server).
 *
 * berserkBlockBreaking reads the block and the block entity at every position of its loop and uses the block entity only
 * after if (block.isAir() || immune) continue. getBlockEntity is not asked where the block just read is air (see
 * BlockBreakReads); the loop is unchanged otherwise.
 *
 * Ported to 1.21.1: berserkBlockBreaking is unchanged in 3.33. Its other loop, BlockBreaking, was rewritten upstream
 * (BlockPos.betweenClosed over the bounding box, getBlockEntity only after the air / immune check): it no longer reads
 * block entities at air, so only berserkBlockBreaking is wrapped now.
 */
@Mixin(value = Netherite_Monstrosity_Entity.class, remap = false)
public abstract class MonstrosityBlockBreakingMixin {
    @Unique
    private final BlockBreakReads bons$reads = new BlockBreakReads();

    @WrapOperation(method = "berserkBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState bons$block(Level level, BlockPos pos, Operation<BlockState> original) {
        return this.bons$reads.read(level, pos, original);
    }

    @WrapOperation(method = "berserkBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity bons$blockEntity(Level level, BlockPos pos, Operation<BlockEntity> original) {
        return this.bons$reads.blockEntity(level, pos, original, true);
    }
}
