package bons.furious.mixin.ribbits_search;

import bons.furious.patch.ribbits_search.RibbitsSearch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import com.yungnickyoung.minecraft.ribbits.entity.goal.RibbitFishGoal;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ribbits_goal_search_sections (Ribbits 3.0.2; server side; since 1.0.45): RibbitFishGoal.canUse (canUse) asks
 * BlockPos.findClosestMatch(pos, 16, 5, "water with air above") every other daytime tick. RibbitsSearch visits the same
 * positions in the same order with the same chunk lookups and runs the goal's own test only in sections holding a state whose
 * fluid is in #water; the rest of canUse (the dry-spot choice with the ribbit's random) runs unchanged. Our own logic only.
 */
@Mixin(value = RibbitFishGoal.class, remap = false)
public abstract class RibbitFishSearchMixin {
    @Shadow
    @Final
    private RibbitEntity ribbit;

    @WrapOperation(method = "canUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;findClosestMatch(Lnet/minecraft/core/BlockPos;IILjava/util/function/Predicate;)Ljava/util/Optional;"))
    private Optional<BlockPos> bons$fishSearch(BlockPos center, int w, int h, Predicate<BlockPos> test, Operation<Optional<BlockPos>> original) {
        return RibbitsSearch.fish(this.ribbit, center, w, h, test, original);
    }
}
