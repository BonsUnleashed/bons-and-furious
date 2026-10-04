package bons.furious.mixin.dynamictrees_c2;

import bons.furious.patch.dynamictrees_c2.RotCycleGuard;
import com.ferreusveritas.dynamictrees.block.branch.BasicRootsBlock;
import com.ferreusveritas.dynamictrees.tree.species.Species;
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
 * dynamictrees_rot_cycle_guard (Dynamic Trees 1.20.1-1.4.11, both sides): BasicRootsBlock.checkForRot, which ends with the
 * same rapid recursion as BasicBranchBlock.checkForRot. Its Species.rot call always asks for the rapid removal, so every root
 * call that reaches it is recorded (one that is not rapid never recurses: its record is only ever the bottom one) and a
 * refused root removal loops, and is cut, the same way. Same two hooks as RotCycleBranchMixin; see RotCycleGuard. MIT
 * target, no Dynamic Trees code carried.
 */
@Mixin(value = BasicRootsBlock.class, remap = false)
public abstract class RotCycleRootsMixin {
    @WrapOperation(method = "checkForRot", at = @At(value = "INVOKE",
            target = "Lcom/ferreusveritas/dynamictrees/tree/species/Species;rot(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;IIILnet/minecraft/util/RandomSource;ZZ)Z"))
    private boolean bons$rotCycle(Species species, LevelAccessor level, BlockPos pos, int neighborCount, int radius, int fertility,
                                  RandomSource rand, boolean rotRapid, boolean growLeaves, Operation<Boolean> original) {
        if (RotCycleGuard.cut(this, species, level, pos, radius, fertility, rand, rotRapid, true)) {
            return false;     // the cut: Species.rot is not called, didRot = false, no recursion
        }
        return original.call(species, level, pos, neighborCount, radius, fertility, rand, rotRapid, growLeaves);
    }

    @ModifyReturnValue(method = "checkForRot", at = @At("TAIL"))
    private boolean bons$rotCycleExit(boolean didRot, @Local(argsOnly = true) LevelAccessor level, @Local(argsOnly = true) BlockPos pos) {
        return RotCycleGuard.exit(this, level, pos, didRot);
    }
}
