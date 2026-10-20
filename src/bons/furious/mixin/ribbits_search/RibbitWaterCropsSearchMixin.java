package bons.furious.mixin.ribbits_search;

import bons.furious.patch.ribbits_search.RibbitsSearch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import com.yungnickyoung.minecraft.ribbits.entity.goal.RibbitWaterCropsGoal;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ribbits_goal_search_sections (Ribbits 3.0.2; server side; since 1.0.45): RibbitWaterCropsGoal.canUse (m_8036_) asks
 * BlockPos.findClosestMatch(pos, 16, 5, crop test) every other daytime tick. RibbitsSearch visits the same positions in the
 * same order with the same chunk lookups and runs the goal's own test wherever it can be anything but a pure false (a section
 * holding a CropBlock state, and only while the buff cooldown is 0); the rest of canUse runs unchanged. Our own logic only
 * (Ribbits is LGPL-3.0; nothing of its code is carried).
 */
@Mixin(value = RibbitWaterCropsGoal.class, remap = false)
public abstract class RibbitWaterCropsSearchMixin {
    @Shadow
    @Final
    private RibbitEntity ribbit;

    @WrapOperation(method = "m_8036_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;m_121930_(Lnet/minecraft/core/BlockPos;IILjava/util/function/Predicate;)Ljava/util/Optional;"))
    private Optional<BlockPos> bons$cropSearch(BlockPos center, int w, int h, Predicate<BlockPos> test, Operation<Optional<BlockPos>> original) {
        return RibbitsSearch.crops(this.ribbit, center, w, h, test, original);
    }
}
