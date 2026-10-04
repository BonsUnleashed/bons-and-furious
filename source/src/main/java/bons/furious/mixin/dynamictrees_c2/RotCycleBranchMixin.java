package bons.furious.mixin.dynamictrees_c2;

import bons.furious.patch.dynamictrees_c2.RotCycleGuard;
import com.dtteam.dynamictrees.block.branch.BasicBranchBlock;
import com.dtteam.dynamictrees.tree.species.Species;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dynamictrees_rot_cycle_guard (Dynamic Trees, MIT; 1.21.1 tested build: dynamictrees-neoforge-1.21.1-1.7.2; both sides):
 * BasicBranchBlock.checkForRot.
 *
 * At its Species.rot call (after the support reads) a rapid call is recorded, or, when the same call is already recorded
 * with no rot write succeeding since, answered "not rotted" without calling Species.rot: exactly where the original recurses
 * until StackOverflowError. The final return drops the record. Two small hooks, so the JIT still inlines the recursion and a
 * long finite cascade needs no more stack than in the original. See RotCycleGuard. MIT target, no Dynamic Trees code carried.
 * Ported to 1.21.1: same method body in 1.7.2 (package com.dtteam.dynamictrees).
 */
@Mixin(value = BasicBranchBlock.class, remap = false)
public abstract class RotCycleBranchMixin {
    @WrapOperation(method = "checkForRot", at = @At(value = "INVOKE",
            target = "Lcom/dtteam/dynamictrees/tree/species/Species;rot(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;IIILnet/minecraft/util/RandomSource;ZZ)Z"))
    private boolean bons$rotCycle(Species species, LevelAccessor level, BlockPos pos, int neighborCount, int radius, int fertility,
                                  RandomSource rand, boolean rotRapid, boolean growLeaves, Operation<Boolean> original) {
        if (RotCycleGuard.cut(this, species, level, pos, radius, fertility, rand, rotRapid, false)) {
            return false;     // the cut: Species.rot is not called, didRot = false, no recursion
        }
        return original.call(species, level, pos, neighborCount, radius, fertility, rand, rotRapid, growLeaves);
    }

    @ModifyReturnValue(method = "checkForRot", at = @At("TAIL"))
    private boolean bons$rotCycleExit(boolean didRot, @Local(argsOnly = true) LevelAccessor level, @Local(argsOnly = true) BlockPos pos) {
        return RotCycleGuard.exit(this, level, pos, didRot);
    }
}
