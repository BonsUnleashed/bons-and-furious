package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.BlockBreakReads;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Old_Netherite_Monstrosity_Entity;

/**
 * cataclysm_boss_block_breaking (Old_Netherite_Monstrosity_Entity, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is carried; server).
 *
 * berserkBlockBreaking and BlockBreaking read the block and the block entity at every position of their loops and use the
 * block entity only inside if (block != AIR.defaultBlockState() && ...). getBlockEntity is not asked where the block just read is air (see BlockBreakReads); the loops
 * are unchanged otherwise.
 */
@Mixin(value = Old_Netherite_Monstrosity_Entity.class, remap = false)
public abstract class OldMonstrosityBlockBreakingMixin {
    @Unique
    private final BlockBreakReads bons$reads = new BlockBreakReads();

    @WrapOperation(method = {"berserkBlockBreaking", "BlockBreaking"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState bons$block(Level level, BlockPos pos, Operation<BlockState> original) {
        return this.bons$reads.read(level, pos, original);
    }

    @WrapOperation(method = {"berserkBlockBreaking", "BlockBreaking"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7702_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity bons$blockEntity(Level level, BlockPos pos, Operation<BlockEntity> original) {
        return this.bons$reads.blockEntity(level, pos, original, false);
    }
}
