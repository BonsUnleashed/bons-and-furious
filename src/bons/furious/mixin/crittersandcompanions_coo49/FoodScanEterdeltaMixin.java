package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.FoodScan;
import com.github.eterdelta.crittersandcompanions.entity.OtterEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * crittersandcompanions_food_scan_interval, Critters and Companions 2.3.5 (OtterEntity$SearchFoodGoal; absent in 2.7.1
 * packs, so the mixin is @Pseudo and CrittersandcompanionsPlugin offers it to Mixin only when the guard matched the 2.3.5
 * classes), server: the item search in canUse goes through FoodScan; the goal's canContinueToUse (Goal's own, which asks
 * canUse) marks its calls so a running goal searches on every check. See FoodScan.
 */
@Pseudo
@Mixin(value = OtterEntity.SearchFoodGoal.class, remap = false)
public abstract class FoodScanEterdeltaMixin extends Goal {
    @Unique
    private boolean bons$continuing;
    @Unique
    private long bons$lastSearch = Long.MIN_VALUE;

    /** canContinueToUse (the goal declares none: Goal's answers canUse()); its canUse calls are never throttled. */
    @Override
    public boolean m_8045_() {
        this.bons$continuing = true;
        try {
            return super.m_8045_();
        } finally {
            this.bons$continuing = false;
        }
    }

    @WrapOperation(method = "m_8036_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<?> bons$searchInterval(Level level, Class<?> type, AABB box, Predicate<?> filter, Operation<List<?>> original) {
        long now = level.m_46467_();
        if (!this.bons$continuing && FoodScan.skip(now, this.bons$lastSearch)) return List.of();   // as an empty search
        this.bons$lastSearch = now;
        return original.call(level, type, box, filter);
    }
}
