package bons.furious.mixin.dynamictrees_c49;

import bons.furious.patch.dynamictrees_c49.DtLean;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * dynamictrees_leaf_site_single_read (Dynamic Trees, MIT; 1.21.1 tested build: dynamictrees-neoforge-1.21.1-1.7.2; both
 * sides): @Inject at HEAD of DynamicLeavesBlock.isLocationSuitableForNewLeaves and of hasAdequateLight, cancelling with
 * DtLean.site / DtLean.light (modified copies of the shipped bodies that read each block once; one body for every level
 * class, hasAdequateLight still called virtually, so subclasses that override it keep their own). With the flag off nothing
 * is cancelled and the shipped bodies run. No MixinExtras @Local: in a @WrapOperation it costs a LocalRef object per call.
 *
 * Ported to 1.21.1: Dynamic Trees 1.7.2 (package com.dtteam.dynamictrees) adds a waterlogging test after the leaves test
 * (a target holding a fluid answers false unless the block's own LeavesProperties is water resistant, read through
 * LeavesPropertiesWaterAccessor) and replaces tall grass with Blocks.SHORT_GRASS; the reads are the same: target, below,
 * below again, then the target through isEmptyBlock and getBlockState(...).canBeReplaced().
 */
@Mixin(value = DynamicLeavesBlock.class, remap = false)
public abstract class LeafSiteReadsMixin {
    @Inject(method = "isLocationSuitableForNewLeaves", at = @At("HEAD"), cancellable = true)
    private void bons$dtSite(LevelAccessor level, LeavesProperties props, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (DtLean.leafSite) cir.setReturnValue(DtLean.site((DynamicLeavesBlock) (Object) this, level, props, pos));
    }

    @Inject(method = "hasAdequateLight", at = @At("HEAD"), cancellable = true)
    private void bons$dtLight(BlockState state, LevelAccessor level, LeavesProperties props, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (DtLean.leafSite) cir.setReturnValue(DtLean.light(state, level, props, pos));
    }
}
