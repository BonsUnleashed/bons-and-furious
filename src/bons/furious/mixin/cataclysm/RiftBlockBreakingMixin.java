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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Leviathan.Dimensional_Rift_Entity;

/**
 * cataclysm_boss_block_breaking (Dimensional_Rift_Entity, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is
 * carried; server).
 *
 * Wraps the level calls inside berserkBlockBreaking's unchanged 31 x 31 x 31 loop (see BlockBreakReads): the block read is
 * answered from the previous iteration's "block above" read at the same position while nothing was written since;
 * getBlockEntity is not asked where the block is air (the loop's test is false there before it is used); removeBlock,
 * setBlock and addFreshEntity clear what is kept. The per-call state is reset at the head of the method.
 */
@Mixin(value = Dimensional_Rift_Entity.class, remap = false)
public abstract class RiftBlockBreakingMixin {
    @Unique
    private final BlockBreakReads bons$reads = new BlockBreakReads();

    @Inject(method = "berserkBlockBreaking", at = @At("HEAD"))
    private void bons$freshReads(int x, int y, int z, CallbackInfo ci) {
        this.bons$reads.reset();
    }

    @WrapOperation(method = "berserkBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 0))
    private BlockState bons$block(Level level, BlockPos pos, Operation<BlockState> original) {
        return this.bons$reads.block(level, pos, original);
    }

    @WrapOperation(method = "berserkBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 1))
    private BlockState bons$blockAbove(Level level, BlockPos pos, Operation<BlockState> original) {
        return this.bons$reads.above(level, pos, original);
    }

    @WrapOperation(method = "berserkBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7702_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity bons$blockEntity(Level level, BlockPos pos, Operation<BlockEntity> original) {
        return this.bons$reads.blockEntity(level, pos, original, false);
    }

    @WrapOperation(method = "berserkBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7471_(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean bons$removed(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        this.bons$reads.written();
        return original.call(level, pos, moving);
    }

    @WrapOperation(method = "berserkBlockBreaking", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7731_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean bons$set(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        this.bons$reads.written();
        return original.call(level, pos, state, flags);
    }

    @WrapOperation(method = "berserkBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean bons$added(Level level, Entity entity, Operation<Boolean> original) {
        this.bons$reads.written();
        return original.call(level, entity);
    }
}
