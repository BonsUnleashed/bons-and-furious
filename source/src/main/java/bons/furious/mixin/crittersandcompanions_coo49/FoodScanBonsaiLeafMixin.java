package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.FoodScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bonsaistudi0s.crittersandcompanions.common.entity.brain.goal.LeafInsectSearchLeavesGoal;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * crittersandcompanions_food_scan_interval, Critters and Companions 2.7.0 for Minecraft 1.21.1 leaf insects
 * (brain.goal.LeafInsectSearchLeavesGoal), server. Same hooks as FoodScanBonsaiOtterMixin. See FoodScan.
 */
@Mixin(value = LeafInsectSearchLeavesGoal.class, remap = false)
public abstract class FoodScanBonsaiLeafMixin extends Goal {
    @Unique
    private boolean bons$continuing;
    @Unique
    private long bons$lastSearch = Long.MIN_VALUE;

    @Override
    public boolean canContinueToUse() {
        this.bons$continuing = true;
        try {
            return super.canContinueToUse();
        } finally {
            this.bons$continuing = false;
        }
    }

    @WrapOperation(method = "canUse()Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<?> bons$searchInterval(Level level, Class<?> type, AABB box, Predicate<?> filter, Operation<List<?>> original) {
        long now = level.getGameTime();
        if (!this.bons$continuing && FoodScan.skip(now, this.bons$lastSearch)) return List.of();
        this.bons$lastSearch = now;
        return original.call(level, type, box, filter);
    }
}
